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

use super::{tags, wire};
use crate::aac::Aac;
use crate::engine::{self, Fetch, Loudness};
use crate::{Encoding, PlaybackConfig, PlaybackEvents, PlaybackFactory, Player, radio};

/// How long a station may take to answer before its load fails.
const CONNECT: Duration = Duration::from_secs(10);

/// A file that opened and decodes, how long the decoder says it is, and the ReplayGain its tags
/// carry. For a radio station, the connection a load opened, which the first decoder takes.
#[derive(Clone)]
pub struct Loaded {
    length: Option<Duration>,
    loudness: Option<Loudness>,
    encoding: Option<Encoding>,
    stream: Option<Arc<Mutex<Option<reqwest::blocking::Response>>>>,
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
            return match opened.map_or_else(|| connect(stream), Ok).and_then(listen) {
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
        response: reqwest::blocking::Response,
        read: u64,
    },
}

impl Read for Audio {
    fn read(&mut self, buf: &mut [u8]) -> io::Result<usize> {
        match self {
            Self::File { file, .. } => file.read(buf),
            Self::Stream { response, read } => {
                let count = response.read(buf)?;
                *read += count as u64;
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

/// Connects to a station's stream. It blocks, so it runs off the async runtime.
fn connect(stream: &str) -> Result<reqwest::blocking::Response> {
    client()
        .get(stream)
        .header("User-Agent", concat!("encore/", env!("CARGO_PKG_VERSION")))
        .send()
        .context("cannot reach the station")?
        .error_for_status()
        .context("the station refused the stream")
}

/// Builds a decoder over a station's stream, guided by the type the station says it sends.
fn listen(response: reqwest::blocking::Response) -> Result<rodio::Decoder<BufReader<Audio>>> {
    let mime = response
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
        });
    let reader = BufReader::new(Audio::Stream { response, read: 0 });
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
