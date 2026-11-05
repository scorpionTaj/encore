//! The remote page: while the setting is on, the desktop serves a small page and a JSON
//! endpoint on the local network, so a phone's browser can see what plays and drive it. Every
//! request has to carry the secret from Settings, so others on the network cannot.

use std::net::{IpAddr, Ipv4Addr, SocketAddr, UdpSocket};
use std::sync::Arc;
use std::time::Duration;

use anyhow::{Context as _, Result};
use gpui::{Context, Entity, Task};
use serde::Serialize;
use tokio::io::{AsyncReadExt as _, AsyncWriteExt as _};
use tokio::net::{TcpListener, TcpStream};
use tokio::sync::{mpsc, watch};
use tokio::task::JoinHandle;

use crate::{AppSettings, Io, Playback, PlaybackState};

/// The port the page is served on, when it is free; another one is picked otherwise.
const PORT: u16 = 7381;
/// The most a request may send before it is turned away, headers included.
const REQUEST_LIMIT: usize = 8 * 1024;
/// How long a phone may take to send its request.
const REQUEST_TIMEOUT: Duration = Duration::from_secs(5);
/// A documentation address no packet is ever sent to, used only to learn which local address
/// the system routes from.
const PROBE: SocketAddr = SocketAddr::new(IpAddr::V4(Ipv4Addr::new(192, 0, 2, 1)), 9);
/// The page itself, with its labels filled in when the remote starts.
const PAGE: &str = include_str!("web_remote.html");
/// The Fluent keys the page shows, by the name its script reads them under.
const LABELS: [(&str, &str); 7] = [
    ("title", "remote-page-title"),
    ("idle", "remote-page-idle"),
    ("play", "remote-page-play"),
    ("pause", "remote-page-pause"),
    ("previous", "remote-page-previous"),
    ("next", "remote-page-next"),
    ("volume", "remote-page-volume"),
];

/// A request from the remote page, in the terms `Playback` understands.
#[derive(Clone, Copy, Debug)]
enum Command {
    Toggle,
    Next,
    Previous,
    Seek(Duration),
    Volume(f32),
}

/// What the remote page shows, as its script reads it from `/api/state`.
#[derive(Serialize)]
struct Shown<'a> {
    title: &'a str,
    artist: &'a str,
    album: &'a str,
    playing: bool,
    position_ms: u128,
    duration_ms: u128,
    volume: f32,
}

/// Serves the remote page while `AppSettings::web_remote` is on, and plays what it asks for.
pub struct WebRemote {
    settings: Entity<AppSettings>,
    playback: Entity<Playback>,
    io: Io,
    /// The latest state as JSON, read by every `/api/state` request.
    shown: watch::Sender<String>,
    serving: Option<Serving>,
}

/// A running server, stopped when this is dropped.
struct Serving {
    address: String,
    server: JoinHandle<()>,
    _commands: Task<()>,
}

impl Drop for Serving {
    fn drop(&mut self) {
        self.server.abort();
    }
}

impl WebRemote {
    pub fn new(
        settings: Entity<AppSettings>,
        playback: Entity<Playback>,
        io: Io,
        cx: &mut Context<Self>,
    ) -> Self {
        cx.observe(&settings, |this, _, cx| this.follow(cx))
            .detach();
        cx.observe(&playback, |this, _, cx| this.publish(cx))
            .detach();
        let mut this = Self {
            settings,
            playback,
            io,
            shown: watch::Sender::new(String::new()),
            serving: None,
        };
        this.follow(cx);
        this
    }

    /// The address to open on a phone, secret included, while the page is served.
    pub fn address(&self) -> Option<&str> {
        self.serving
            .as_ref()
            .map(|serving| serving.address.as_str())
    }

    /// Starts or stops the server to match the setting.
    fn follow(&mut self, cx: &mut Context<Self>) {
        let settings = self.settings.read(cx);
        let wanted = settings.web_remote() && !settings.web_remote_token().is_empty();
        match (wanted, self.serving.is_some()) {
            (true, false) => {
                let token = settings.web_remote_token().to_owned();
                match self.start(token, cx) {
                    Ok(serving) => self.serving = Some(serving),
                    Err(error) => log::warn!("remote page: cannot start: {error:#}"),
                }
                self.publish(cx);
                cx.notify();
            }
            (false, true) => {
                self.serving = None;
                cx.notify();
            }
            _ => {}
        }
    }

    fn start(&self, token: String, cx: &mut Context<Self>) -> Result<Serving> {
        let listener = std::net::TcpListener::bind((Ipv4Addr::UNSPECIFIED, PORT))
            .or_else(|_| std::net::TcpListener::bind((Ipv4Addr::UNSPECIFIED, 0)))
            .context("cannot open a port")?;
        listener
            .set_nonblocking(true)
            .context("cannot make the port non-blocking")?;
        let port = listener
            .local_addr()
            .context("cannot read the port")?
            .port();
        let host = local_address().unwrap_or(IpAddr::V4(Ipv4Addr::LOCALHOST));
        let address = format!("http://{host}:{port}/?token={token}");

        let (sender, mut receiver) = mpsc::unbounded_channel();
        let site = Arc::new(Site {
            token,
            page: page(),
            shown: self.shown.subscribe(),
            commands: sender,
        });
        let server = self.io.spawn(async move {
            let listener = match TcpListener::from_std(listener) {
                Ok(listener) => listener,
                Err(error) => return log::warn!("remote page: cannot listen: {error}"),
            };
            loop {
                let Ok((stream, _)) = listener.accept().await else {
                    continue;
                };
                let site = site.clone();
                tokio::spawn(async move {
                    if let Err(error) = site.answer(stream).await {
                        log::debug!("remote page: dropped a request: {error:#}");
                    }
                });
            }
        });
        let _commands = cx.spawn(async move |this, cx| {
            while let Some(command) = receiver.recv().await {
                if this.update(cx, |this, cx| this.act(command, cx)).is_err() {
                    break;
                }
            }
        });
        log::info!("remote page: serving on port {port}");
        Ok(Serving {
            address,
            server,
            _commands,
        })
    }

    fn act(&mut self, command: Command, cx: &mut Context<Self>) {
        self.playback.update(cx, |playback, cx| match command {
            Command::Toggle => playback.toggle_play(cx),
            Command::Next => playback.next(cx),
            Command::Previous => playback.previous(cx),
            Command::Seek(at) => playback.seek(at, cx),
            Command::Volume(level) => playback.set_volume(level, cx),
        });
    }

    /// Hands the page's script the current state, while the page is served.
    fn publish(&mut self, cx: &mut Context<Self>) {
        if self.serving.is_none() {
            return;
        }
        let playback = self.playback.read(cx);
        let track = playback.track();
        let shown = Shown {
            title: track.map_or("", |track| track.name.as_str()),
            artist: track.map_or("", |track| track.artists.as_str()),
            album: track.map_or("", |track| track.album.as_str()),
            playing: matches!(playback.state(), PlaybackState::Playing),
            position_ms: playback.position().as_millis(),
            duration_ms: track.map_or(0, |track| track.duration.as_millis()),
            volume: playback.volume(),
        };
        match serde_json::to_string(&shown) {
            Ok(json) => {
                self.shown.send_if_modified(|current| {
                    let changed = *current != json;
                    *current = json;
                    changed
                });
            }
            Err(error) => log::warn!("remote page: cannot describe the state: {error}"),
        }
    }
}

/// What every request is answered from, shared by the connections.
struct Site {
    token: String,
    page: String,
    shown: watch::Receiver<String>,
    commands: mpsc::UnboundedSender<Command>,
}

impl Site {
    /// Reads one request from `stream` and writes its answer, then closes the connection.
    async fn answer(&self, mut stream: TcpStream) -> Result<()> {
        let mut request = Vec::with_capacity(1024);
        let mut chunk = [0u8; 1024];
        tokio::time::timeout(REQUEST_TIMEOUT, async {
            while !request.windows(4).any(|w| w == b"\r\n\r\n") {
                let read = stream.read(&mut chunk).await?;
                if read == 0 || request.len() + read > REQUEST_LIMIT {
                    break;
                }
                request.extend_from_slice(&chunk[..read]);
            }
            anyhow::Ok(())
        })
        .await
        .context("the request took too long")??;

        let head = String::from_utf8_lossy(&request);
        let line = head.lines().next().unwrap_or_default();
        let mut parts = line.split(' ');
        let (method, target) = (
            parts.next().unwrap_or_default(),
            parts.next().unwrap_or("/"),
        );
        let (path, query) = target.split_once('?').unwrap_or((target, ""));

        let response = match self.route(method, path, query) {
            Reply::Page => respond("200 OK", "text/html; charset=utf-8", &self.page),
            Reply::State => respond("200 OK", "application/json", &self.shown.borrow()),
            Reply::Done => respond("204 No Content", "text/plain", ""),
            Reply::Forbidden => respond("403 Forbidden", "text/plain", ""),
            Reply::Missing => respond("404 Not Found", "text/plain", ""),
        };
        stream.write_all(response.as_bytes()).await?;
        stream.shutdown().await?;
        Ok(())
    }

    fn route(&self, method: &str, path: &str, query: &str) -> Reply {
        if param(query, "token") != Some(self.token.as_str()) {
            return Reply::Forbidden;
        }
        let command = match (method, path) {
            ("GET", "/") => return Reply::Page,
            ("GET", "/api/state") => return Reply::State,
            ("POST", "/api/toggle") => Command::Toggle,
            ("POST", "/api/next") => Command::Next,
            ("POST", "/api/previous") => Command::Previous,
            ("POST", "/api/seek") => match param(query, "ms").and_then(|ms| ms.parse().ok()) {
                Some(ms) => Command::Seek(Duration::from_millis(ms)),
                None => return Reply::Missing,
            },
            ("POST", "/api/volume") => {
                match param(query, "level").and_then(|level| level.parse::<f32>().ok()) {
                    Some(level) => Command::Volume(level.clamp(0.0, 1.0)),
                    None => return Reply::Missing,
                }
            }
            _ => return Reply::Missing,
        };
        // The app closing drops the receiver; the page simply stops answering then.
        self.commands.send(command).ok();
        Reply::Done
    }
}

/// How a request is answered.
enum Reply {
    Page,
    State,
    Done,
    Forbidden,
    Missing,
}

/// The remote page with its labels in the app's language.
fn page() -> String {
    let labels: serde_json::Map<String, serde_json::Value> = LABELS
        .iter()
        .map(|(name, key)| {
            (
                (*name).to_owned(),
                i18n::lookup(key, None).to_string().into(),
            )
        })
        .collect();
    let labels = serde_json::Value::Object(labels).to_string();
    // Kept from closing the script tag it sits in.
    PAGE.replace("{{LABELS}}", &labels.replace("</", "<\\/"))
}

/// One query parameter's value, as sent.
fn param<'a>(query: &'a str, name: &str) -> Option<&'a str> {
    query
        .split('&')
        .filter_map(|pair| pair.split_once('='))
        .find_map(|(key, value)| (key == name).then_some(value))
}

fn respond(status: &str, kind: &str, body: &str) -> String {
    format!(
        "HTTP/1.1 {status}\r\nContent-Type: {kind}\r\nContent-Length: {}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n{body}",
        body.len()
    )
}

/// The address other machines on the network reach this one at, learned from the route the
/// system would take; connecting a UDP socket sends nothing.
fn local_address() -> Option<IpAddr> {
    let socket = UdpSocket::bind((Ipv4Addr::UNSPECIFIED, 0)).ok()?;
    socket.connect(PROBE).ok()?;
    Some(socket.local_addr().ok()?.ip())
}
