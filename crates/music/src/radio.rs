//! Moroccan internet radio: the stations Radio Browser lists for Morocco, and a station as a
//! track the engine can play.
//!
//! A station's track id carries its stream address after [`RADIO_PREFIX`], so a queue or a
//! history row that names a station needs nothing else to play it again.

use std::collections::HashSet;
use std::time::Duration;

use anyhow::{Context as _, Result};
use serde::Deserialize;

use crate::Track;

pub const RADIO_PREFIX: &str = "radio:";

/// Radio Browser's search for Moroccan stations that its checks found working, most played
/// first.
const STATIONS: &str = "https://all.api.radio-browser.info/json/stations/search\
                        ?countrycode=MA&hidebroken=true&order=clickcount&reverse=true&limit=300";
const AGENT: &str = concat!(
    "encore/",
    env!("CARGO_PKG_VERSION"),
    " (https://github.com/scorpionTaj/encore)"
);
/// The stream formats the decoder plays. HLS playlists and anything else are left out.
const PLAYABLE: [&str; 4] = ["MP3", "AAC", "AAC+", "OGG"];
/// How many tags a station's card names.
const TAGS: usize = 3;

/// One station as Radio Browser lists it.
#[derive(Clone, Debug, PartialEq, Eq, Deserialize)]
pub struct Station {
    #[serde(rename = "stationuuid")]
    pub id: String,
    pub name: String,
    /// The stream address with any playlist file already followed.
    #[serde(rename = "url_resolved")]
    pub stream: String,
    #[serde(default)]
    pub favicon: String,
    #[serde(default)]
    pub tags: String,
    #[serde(default)]
    pub codec: String,
    #[serde(default)]
    pub bitrate: u32,
    #[serde(default)]
    hls: u8,
}

impl Station {
    /// The station's genres as a short line, at most [`TAGS`] of them.
    pub fn genres(&self) -> String {
        self.tags
            .split(',')
            .map(str::trim)
            .filter(|tag| !tag.is_empty())
            .take(TAGS)
            .collect::<Vec<_>>()
            .join(", ")
    }

    /// The station as a track: its name for the title, its genres in place of an artist, and no
    /// length, since a live stream has none.
    pub fn track(&self) -> Track {
        Track {
            id: Some(format!("{RADIO_PREFIX}{}", self.stream)),
            name: self.name.trim().to_owned(),
            playable: true,
            artists: self.genres(),
            artist_refs: Vec::new(),
            album: String::new(),
            album_id: None,
            cover: (!self.favicon.is_empty()).then(|| self.favicon.clone()),
            duration: Duration::ZERO,
            added_at: None,
            modified_at: None,
            added_by: None,
            playcount: None,
            popularity: 0,
            explicit: false,
            track_number: 0,
            disc_number: 0,
            tags: Vec::new(),
            languages: Vec::new(),
            credits: Vec::new(),
        }
    }

    fn playable(&self) -> bool {
        self.hls == 0
            && !self.stream.is_empty()
            && PLAYABLE.contains(&self.codec.to_ascii_uppercase().as_str())
    }
}

/// Whether a track id names a radio station.
pub fn is_radio_id(id: &str) -> bool {
    id.starts_with(RADIO_PREFIX)
}

/// The stream address a station's track id carries.
pub fn stream_of(id: &str) -> Option<&str> {
    id.strip_prefix(RADIO_PREFIX)
}

/// The Moroccan stations that play, most listened to first, each name listed once.
pub async fn stations(http: &reqwest::Client) -> Result<Vec<Station>> {
    let listed: Vec<Station> = http
        .get(STATIONS)
        .header("User-Agent", AGENT)
        .send()
        .await
        .context("cannot reach radio browser")?
        .error_for_status()
        .context("radio browser refused the station list")?
        .json()
        .await
        .context("cannot read the station list")?;
    Ok(playable(listed))
}

/// Keeps the stations the decoder can play, and the first of any that share a name or a stream.
fn playable(listed: Vec<Station>) -> Vec<Station> {
    let mut names = HashSet::new();
    let mut streams = HashSet::new();
    listed
        .into_iter()
        .filter(Station::playable)
        .filter(|station| {
            let named = names.insert(station.name.trim().to_lowercase());
            let streamed = streams.insert(station.stream.trim_end_matches('.').to_owned());
            named && streamed
        })
        .collect()
}
