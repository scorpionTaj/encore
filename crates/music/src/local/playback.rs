//! Playback for files on disk: what [`crate::engine`] needs that is local's own.
//!
//! A track id names a file, so a load only reads its header for the length and its tags for the
//! ReplayGain, and a decoder opens over the file itself. A radio station's id names a stream
//! instead, which a load connects to and a decoder reads as it arrives. The threads, the queue,
//! the preload, the gapless join and the loudness gain are the engine's.

use std::fs::File;
use std::io::{self, BufReader, Read, Seek, SeekFrom};
use std::sync::{Arc, Mutex, OnceLock};
use std::time::Duration;

use anyhow::{Context as _, Result, anyhow};
use async_trait::async_trait;
use rodio::Source as _;

use super::hls::Hls;
use super::{tags, wire};
use crate::aac::Aac;
use crate::engine::{self, Fetch, Loudness};
use crate::{Encoding, PlaybackConfig, PlaybackEvents, PlaybackFactory, Player, radio};

/// How long a station may take to answer before its load fails.
const CONNECT: Duration = Duration::from_secs(10);
const AGENT: &str = concat!("encore/", env!("CARGO_PKG_VERSION"));

/// A file that opened and decodes, how long the decoder says it is, and the ReplayGain its tags
/// carry. For a radio station, the connection a load opened, which the first decoder takes.
#[derive(Clone)]
pub struct Loaded {
    length: Option<Duration>,
    loudness: Option<Loudness>,
    encoding: Option<Encoding>,
    stream: Option<Arc<Mutex<Option<Live>>>>,
}

#[derive(Default)]
pub struct Factory;

impl PlaybackFactory for Factory {
    fn start(&self, config: PlaybackConfig) -> (Box<dyn Player>, Box<dyn PlaybackEvents>) {
        engine::start(Local, config)
    }
}

struct Local;

#[async_trait]
impl Fetch for Local {
    type Loaded = Loaded;
    type Source = rodio::Decoder<BufReader<Audio>>;

    fn name(&self) -> &'static str {
        "local"
    }

    /// Opens the file off the engine's runtime to check it decodes, and reads its length, its
    /// ReplayGain and its encoding while it is there.
    async fn load(&self, id: &str) -> Result<Loaded> {
        let id = id.to_owned();
        if let Some(stream) = radio::stream_of(&id).map(str::to_owned) {
            let response = tokio::task::spawn_blocking(move || connect(&stream))
                .await
                .context("cannot connect to the station")??;
            return Ok(Loaded {
                length: None,
                loudness: None,
                encoding: None,
                stream: Some(Arc::new(Mutex::new(Some(response)))),
            });
        }
        tokio::task::spawn_blocking(move || {
            let length = decode(&id)?.total_duration();
            let tagged = wire::path_from_track_id(&id).and_then(tags::tagged);
            Ok(Loaded {
                length,
                loudness: tagged.as_ref().and_then(tags::loudness),
                encoding: tagged.as_ref().and_then(tags::encoding),
                stream: None,
            })
        })
        .await
        .context("cannot probe the local file")?
    }

    fn length(&self, loaded: &Loaded) -> Option<Duration> {
        loaded.length
    }

    fn loudness(&self, loaded: &Loaded) -> Option<Loudness> {
        loaded.loudness
    }

    fn encoding(&self, loaded: &Loaded) -> Option<Encoding> {
        loaded.encoding
    }

    fn open(&self, id: &str, loaded: &Loaded, at: Duration) -> Option<Self::Source> {
        if let Some(stream) = radio::stream_of(id) {
            // The connection the load opened is used once; a second decoder connects again.
            let opened = loaded
                .stream
                .as_ref()
                .and_then(|held| held.lock().ok()?.take());
            return match opened
                .map_or_else(|| connect(stream), Ok)
                .and_then(|live| listen(live, stream))
            {
                Ok(decoder) => Some(decoder),
                Err(error) => {
                    log::warn!("playback: cannot play the station {stream}: {error:#}");
                    None
                }
            };
        }
        let mut decoder = match decode(id) {
            Ok(decoder) => decoder,
            Err(error) => {
                log::warn!("playback: cannot decode the local track {id}: {error:#}");
                return None;
            }
        };
        if !at.is_zero()
            && let Err(error) = decoder.try_seek(at)
        {
            log::warn!("playback: cannot start {id} at {}s: {error}", at.as_secs());
        }
        Some(decoder)
    }
}

/// A station's audio as it arrives: one endless HTTP response, or the segments of an HLS
/// playlist joined end to end.
enum Live {
    Http(Box<Icy>),
    Hls(Box<Hls>),
}

/// An HTTP stream that may carry ICY metadata: every `interval` bytes of audio, a length byte
/// and that many 16-byte blocks naming the song now on. The blocks are taken out, so the
/// decoder sees only audio, and the song's title goes to `radio::hear`.
struct Icy {
    response: reqwest::blocking::Response,
    stream: String,
    /// How many audio bytes come between two metadata blocks, `None` when the station sends none.
    interval: Option<usize>,
    /// How many audio bytes are left before the next metadata block.
    left: usize,
}

impl Icy {
    fn new(response: reqwest::blocking::Response, stream: &str) -> Self {
        let interval = response
            .headers()
            .get("icy-metaint")
            .and_then(|value| value.to_str().ok())
            .and_then(|value| value.trim().parse::<usize>().ok())
            .filter(|interval| *interval > 0);
        Self {
            response,
            stream: stream.to_owned(),
            interval,
            left: interval.unwrap_or(0),
        }
    }

    /// Reads one metadata block and keeps the title it names.
    fn metadata(&mut self) -> io::Result<()> {
        let mut length = [0u8; 1];
        self.response.read_exact(&mut length)?;
        let mut block = vec![0u8; usize::from(length[0]) * 16];
        self.response.read_exact(&mut block)?;
        if let Some(title) = stream_title(&block) {
            radio::hear(&self.stream, title);
        }
        Ok(())
    }
}

impl Read for Icy {
    fn read(&mut self, buf: &mut [u8]) -> io::Result<usize> {
        let Some(interval) = self.interval else {
            return self.response.read(buf);
        };
        if self.left == 0 {
            self.metadata()?;
            self.left = interval;
        }
        let wanted = buf.len().min(self.left);
        let count = self.response.read(&mut buf[..wanted])?;
        self.left -= count;
        Ok(count)
    }
}

impl Live {
    /// The type of audio the station sends, when it says.
    fn mime(&self) -> Option<String> {
        match self {
            Self::Http(icy) => icy
                .response
                .headers()
                .get(reqwest::header::CONTENT_TYPE)
                .and_then(|value| value.to_str().ok())
                .map(|value| {
                    value
                        .split(';')
                        .next()
                        .unwrap_or_default()
                        .trim()
                        .to_owned()
                }),
            Self::Hls(hls) => Some(hls.mime().to_owned()),
        }
    }
}

impl Read for Live {
    fn read(&mut self, buf: &mut [u8]) -> io::Result<usize> {
        match self {
            Self::Http(icy) => icy.read(buf),
            Self::Hls(hls) => hls.read(buf),
        }
    }
}

/// What a decoder reads. A file is read from `skip` on, with every position counted from there,
/// so the decoder never sees the ID3v2 tag in front of the audio: a seek back to the first frame
/// then lands on that frame, not inside the tag, where a cover picture can pass for a frame
/// header. A live stream only goes forward, and `read` counts how far it has.
enum Audio {
    File {
        file: File,
        skip: u64,
    },
    Stream {
        live: Live,
        read: u64,
        /// The station's address, so a recording of it gets what is read.
        stream: String,
    },
}

impl Read for Audio {
    fn read(&mut self, buf: &mut [u8]) -> io::Result<usize> {
        match self {
            Self::File { file, .. } => file.read(buf),
            Self::Stream { live, read, stream } => {
                let count = live.read(buf)?;
                *read += count as u64;
                radio::record(stream, &buf[..count]);
                Ok(count)
            }
        }
    }
}

impl Seek for Audio {
    fn seek(&mut self, pos: SeekFrom) -> io::Result<u64> {
        match self {
            Self::File { file, skip } => {
                let pos = match pos {
                    SeekFrom::Start(at) => SeekFrom::Start(at.saturating_add(*skip)),
                    relative => relative,
                };
                Ok(file.seek(pos)?.saturating_sub(*skip))
            }
            // Asking where the stream is answers; moving it cannot.
            Self::Stream { read, .. } => match pos {
                SeekFrom::Current(0) => Ok(*read),
                SeekFrom::Start(at) if at == *read => Ok(*read),
                _ => Err(io::Error::new(
                    io::ErrorKind::Unsupported,
                    "a live stream cannot seek",
                )),
            },
        }
    }
}

/// The client every station connects through. It waits [`CONNECT`] for an answer and then reads
/// for as long as the station sends.
fn client() -> &'static reqwest::blocking::Client {
    static CLIENT: OnceLock<reqwest::blocking::Client> = OnceLock::new();
    CLIENT.get_or_init(|| {
        reqwest::blocking::Client::builder()
            .connect_timeout(CONNECT)
            .timeout(None)
            .build()
            .unwrap_or_default()
    })
}

/// Connects to a station's stream, or to its HLS playlist and first segment. It blocks, so it
/// runs off the async runtime.
fn connect(stream: &str) -> Result<Live> {
    if radio::is_hls(stream) {
        return Hls::open(client(), AGENT, stream).map(|hls| Live::Hls(Box::new(hls)));
    }
    client()
        .get(stream)
        .header("User-Agent", AGENT)
        .header("Icy-MetaData", "1")
        .send()
        .context("cannot reach the station")?
        .error_for_status()
        .context("the station refused the stream")
        .map(|response| Live::Http(Box::new(Icy::new(response, stream))))
}

/// Builds a decoder over a station's stream, guided by the type the station says it sends.
fn listen(live: Live, stream: &str) -> Result<rodio::Decoder<BufReader<Audio>>> {
    let mime = live.mime();
    radio::sends(stream, mime.as_deref());
    let reader = BufReader::new(Audio::Stream {
        live,
        read: 0,
        stream: stream.to_owned(),
    });
    let mut builder = rodio::Decoder::builder()
        .with_symphonia_decoder::<Aac>()
        .with_data(reader)
        .with_seekable(false)
        .with_gapless(false);
    if let Some(mime) = mime.as_deref() {
        builder = builder.with_mime_type(mime);
    }
    builder.build().context("cannot decode the stream")
}

/// Opens a local file and builds its decoder.
fn decode(id: &str) -> Result<rodio::Decoder<BufReader<Audio>>> {
    let path =
        wire::path_from_track_id(id).ok_or_else(|| anyhow!("{id} is not a local track id"))?;
    let mut file =
        std::fs::File::open(path).with_context(|| format!("cannot open {}", path.display()))?;
    let length = file.metadata().ok().map(|meta| meta.len());

    let skip = wire::id3v2_end(path);
    if skip > 0 {
        let _ = file.seek(SeekFrom::Start(skip));
    }
    let gapless = !wire::has_lying_xing_frame_count(path, skip);
    let reader = BufReader::new(Audio::File { file, skip });

    let mut builder = rodio::Decoder::builder()
        .with_symphonia_decoder::<Aac>()
        .with_data(reader)
        .with_seekable(true)
        .with_gapless(gapless);

    if let Some(length) = length {
        builder = builder.with_byte_len(length.saturating_sub(skip));
    }
    builder.build().context("cannot decode audio")
}

/// The song an ICY metadata block names after `StreamTitle=`, when it names one.
fn stream_title(block: &[u8]) -> Option<String> {
    let text = String::from_utf8_lossy(block);
    let start = text.find("StreamTitle='")? + "StreamTitle='".len();
    let end = text[start..]
        .find("';")
        .map_or(text.len(), |end| start + end);
    let title = text[start..end].trim();
    (!title.is_empty()).then(|| title.to_owned())
}
