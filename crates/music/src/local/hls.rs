//! Live HLS radio read as one continuous stream.
//!
//! Only packed audio is understood: segments that are plain AAC (ADTS) or MP3 frames, each with
//! an ID3 tag in front, which is what radio stations serve. The tags are dropped and the frames
//! joined, so the decoder sees one endless file. Segments in MPEG-TS are refused.

use std::collections::VecDeque;
use std::io::{self, Cursor, Read};
use std::thread;
use std::time::Duration;

use anyhow::{Context as _, Result, bail};
use reqwest::Url;
use reqwest::blocking::Client;

/// How many of the newest segments a station starts from, so playback begins close to live
/// with a little audio in hand.
const LEAD: usize = 3;
/// The wait between playlist reloads when a station names no target duration.
const FALLBACK_WAIT: Duration = Duration::from_secs(3);
/// The first byte of every MPEG-TS packet.
const TS_SYNC: u8 = 0x47;

/// A station's media playlist and the segments of it not yet read.
pub(super) struct Hls {
    client: Client,
    agent: &'static str,
    playlist: Url,
    /// The media sequence number of the next segment to queue.
    next: u64,
    queued: VecDeque<Url>,
    segment: Cursor<Vec<u8>>,
    wait: Duration,
    mime: &'static str,
}

impl Hls {
    /// Follows a master playlist to its first variant, queues the newest segments and reads the
    /// first of them, so a station that serves nothing playable fails here rather than in the
    /// decoder.
    pub(super) fn open(client: &Client, agent: &'static str, address: &str) -> Result<Self> {
        let mut playlist = Url::parse(address).context("the playlist address is not a url")?;
        let text = fetch_text(client, agent, &playlist)?;
        if text.contains("#EXT-X-STREAM-INF") {
            let variant = uris(&text)
                .next()
                .context("the playlist names no variant")?;
            playlist = playlist
                .join(variant)
                .context("the variant address is not a url")?;
        }
        let mut hls = Self {
            client: client.clone(),
            agent,
            playlist,
            next: 0,
            queued: VecDeque::new(),
            segment: Cursor::new(Vec::new()),
            wait: FALLBACK_WAIT,
            mime: "audio/aac",
        };
        hls.refresh()?;
        while hls.queued.len() > LEAD {
            hls.queued.pop_front();
        }
        if let Some(first) = hls.queued.front() {
            hls.mime = match first.path().to_ascii_lowercase().ends_with(".mp3") {
                true => "audio/mpeg",
                false => "audio/aac",
            };
        }
        hls.advance()?;
        Ok(hls)
    }

    /// The type of audio the segments carry, for the decoder.
    pub(super) fn mime(&self) -> &'static str {
        self.mime
    }

    /// Reloads the media playlist and queues the segments that are new since the last load.
    fn refresh(&mut self) -> Result<()> {
        let text = fetch_text(&self.client, self.agent, &self.playlist)?;
        let mut sequence = 0;
        for line in text.lines() {
            if let Some(value) = line.strip_prefix("#EXT-X-MEDIA-SEQUENCE:") {
                sequence = value.trim().parse().unwrap_or(0);
            } else if let Some(value) = line.strip_prefix("#EXT-X-TARGETDURATION:") {
                let target = value.trim().parse::<u64>().unwrap_or(0);
                if target > 0 {
                    self.wait = Duration::from_secs(target).div_f32(2.);
                }
            }
        }
        for (offset, uri) in uris(&text).enumerate() {
            let number = sequence + offset as u64;
            if number < self.next {
                continue;
            }
            self.queued.push_back(
                self.playlist
                    .join(uri)
                    .context("a segment address is not a url")?,
            );
            self.next = number + 1;
        }
        Ok(())
    }

    /// Reads the next segment into place, reloading the playlist and waiting for the station
    /// when every queued segment has been read.
    fn advance(&mut self) -> Result<()> {
        let address = loop {
            if let Some(address) = self.queued.pop_front() {
                break address;
            }
            self.refresh()?;
            if self.queued.is_empty() {
                thread::sleep(self.wait);
            }
        };
        let bytes = self
            .client
            .get(address)
            .header("User-Agent", self.agent)
            .send()
            .context("cannot reach a segment")?
            .error_for_status()
            .context("the station refused a segment")?
            .bytes()
            .context("cannot read a segment")?;
        let audio = &bytes[id3_length(&bytes).min(bytes.len())..];
        if audio.first() == Some(&TS_SYNC) {
            bail!("the station sends MPEG-TS segments, which Encore cannot play");
        }
        self.segment = Cursor::new(audio.to_vec());
        Ok(())
    }
}

impl Read for Hls {
    fn read(&mut self, buf: &mut [u8]) -> io::Result<usize> {
        loop {
            let count = self.segment.read(buf)?;
            if count > 0 || buf.is_empty() {
                return Ok(count);
            }
            self.advance().map_err(io::Error::other)?;
        }
    }
}

fn fetch_text(client: &Client, agent: &str, address: &Url) -> Result<String> {
    client
        .get(address.clone())
        .header("User-Agent", agent)
        .send()
        .context("cannot reach the playlist")?
        .error_for_status()
        .context("the station refused the playlist")?
        .text()
        .context("cannot read the playlist")
}

/// The playlist's entries: every line that is not blank and not a tag.
fn uris(text: &str) -> impl Iterator<Item = &str> {
    text.lines()
        .map(str::trim)
        .filter(|line| !line.is_empty() && !line.starts_with('#'))
}

/// How many bytes the ID3v2 tag in front of a segment takes, or 0 when it has none.
fn id3_length(bytes: &[u8]) -> usize {
    let [b'I', b'D', b'3', _, _, flags, a, b, c, d, ..] = bytes else {
        return 0;
    };
    let size = [a, b, c, d].iter().fold(0usize, |size, byte| {
        (size << 7) | usize::from(**byte & 0x7f)
    });
    let footer = match flags & 0x10 {
        0 => 0,
        _ => 10,
    };
    10 + size + footer
}
