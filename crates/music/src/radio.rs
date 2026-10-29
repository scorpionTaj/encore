//! Internet radio: the stations Radio Browser lists for a country, Morocco first, and a station
//! as a track the engine can play.
//!
//! A station's track id carries its stream address after [`RADIO_PREFIX`], so a queue or a
//! history row that names a station needs nothing else to play it again.

use std::collections::HashMap;
use std::fs::File;
use std::io::Write as _;
use std::path::{Path, PathBuf};
use std::sync::Mutex;
use std::time::Duration;

use anyhow::{Context as _, Result};
use serde::Deserialize;

use crate::Track;

pub const RADIO_PREFIX: &str = "radio:";

/// Radio Browser's search for one country's stations that its checks found working, most played
/// first. The country's code follows.
const STATIONS: &str = "https://all.api.radio-browser.info/json/stations/search\
                        ?hidebroken=true&order=clickcount&reverse=true&limit=300&countrycode=";
/// The countries the radio page offers, by ISO 3166 code, Morocco first.
pub const COUNTRIES: [&str; 16] = [
    "MA", "DZ", "TN", "LY", "MR", "EG", "SA", "AE", "QA", "LB", "FR", "BE", "ES", "GB", "US", "CA",
];
/// The country the radio page opens on until another is picked.
pub const HOME_COUNTRY: &str = "MA";
/// Stations heard in Morocco that Radio Browser files under another country: Monte Carlo
/// Doualiya, Urban Hit Maroc and 4U Classic Rock.
const ABROAD: &str = "https://all.api.radio-browser.info/json/stations/byuuid?uuids=\
                      c5c08ef9-5559-4073-87a8-80bff2b4860b,\
                      422c303d-2e17-11ea-aa0c-52543be04c81,\
                      71066c31-3c0f-47bf-ac4e-2ec0e417fba5";
/// The prefixes some Radio Browser entries put before a station's name.
const PREFIXES: [&str; 2] = ["MA:-", "MRC:-"];
/// Station logos from the stations' own sites, for the ones whose Radio Browser entry has none
/// or one that no longer loads. A station takes the logo of the first fragment its lowercase
/// name contains.
const LOGOS: [(&str, &str); 11] = [
    (
        "radio mars",
        "https://radiomars.ma/wp-content/uploads/2026/03/cropped-radio_mars-192x192.png",
    ),
    (
        "med radio",
        "https://medradio.ma/wp-content/uploads/2023/08/cropped-med-fav-jpg-192x192.jpg",
    ),
    (
        "medi1",
        "https://www.medi1.com/assets/favicon/apple-touch-icon.png",
    ),
    (
        "medi 1",
        "https://www.medi1.com/assets/favicon/apple-touch-icon.png",
    ),
    (
        "hit radio",
        "https://www.hitradio.ma/small-logo-hitradio.png",
    ),
    (
        "ness radio",
        "https://nessradio.com/wp-content/uploads/2026/09/cropped-ness-radio-favicon-192x192.png",
    ),
    (
        "cap radio",
        "https://capradiotv.com/wp-content/uploads/2023/11/cropped-LOGO-PNG-CAP-RADIO-192x192.png",
    ),
    (
        "aswat",
        "https://radioaswat.ma/wp-content/themes/aswata108/img/icons/touch.png",
    ),
    (
        "adwaa",
        "https://adwaafm.com/wp-content/uploads/2023/11/cropped-fgfg-1-180x180.png",
    ),
    (
        "sawt al",
        "https://radiolive.ma/logos/radio-sawt-alamal.png",
    ),
    ("skyrock", "https://skyrock.fm/apple-touch-icon.png"),
];
const AGENT: &str = concat!(
    "encore/",
    env!("CARGO_PKG_VERSION"),
    " (https://github.com/scorpionTaj/encore)"
);
/// The stream formats the decoder plays. An HLS station plays only when its playlist carries
/// AAC or MP3 segments.
const PLAYABLE: [&str; 4] = ["MP3", "AAC", "AAC+", "OGG"];
const HLS_PLAYABLE: [&str; 3] = ["MP3", "AAC", "AAC+"];
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
            name: self.name.clone(),
            playable: true,
            artists: self.genres(),
            artist_refs: Vec::new(),
            album: String::new(),
            album_id: None,
            cover: (!self.favicon.is_empty()).then(|| self.favicon.clone()),
            duration: Duration::ZERO,
            added_at: None,
            modified_at: None,
            encoding: None,
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
        let codec = self.codec.to_ascii_uppercase();
        let formats: &[&str] = match self.hls {
            0 => &PLAYABLE,
            _ if is_hls(&self.stream) => &HLS_PLAYABLE,
            _ => &[],
        };
        !self.stream.is_empty() && formats.contains(&codec.as_str())
    }

    /// Drops the prefix some entries carry and the stray spaces around and inside the name, and
    /// puts a station's own logo in place of a missing or broken one.
    fn tidy(mut self) -> Self {
        let name = PREFIXES.iter().fold(self.name.trim(), |name, prefix| {
            name.strip_prefix(prefix).unwrap_or(name)
        });
        self.name = name.split_whitespace().collect::<Vec<_>>().join(" ");
        let lower = self.name.to_lowercase();
        if let Some((_, logo)) = LOGOS.iter().find(|(fragment, _)| lower.contains(fragment)) {
            self.favicon = (*logo).to_owned();
        }
        self
    }

    /// The stream address without the scheme, query and trailing marks that make one stream look
    /// like two.
    fn stream_key(&self) -> String {
        let stream = self.stream.trim();
        let stream = ["https://", "http://"]
            .iter()
            .find_map(|scheme| stream.strip_prefix(scheme))
            .unwrap_or(stream);
        stream
            .split('?')
            .next()
            .unwrap_or_default()
            .trim_end_matches(['/', ';', '.'])
            .to_lowercase()
    }
}

/// Whether a track id names a radio station.
pub fn is_radio_id(id: &str) -> bool {
    id.starts_with(RADIO_PREFIX)
}

/// Whether a stream address names an HLS playlist rather than the audio itself.
pub fn is_hls(stream: &str) -> bool {
    stream
        .split('?')
        .next()
        .is_some_and(|path| path.to_ascii_lowercase().ends_with(".m3u8"))
}

/// The station being recorded, the file its audio goes to, and where that file is.
struct Recording {
    stream: String,
    file: File,
    path: PathBuf,
}

static RECORDING: Mutex<Option<Recording>> = Mutex::new(None);
/// The kind of audio each station opened so far said it sends, which names a recording's file.
static KINDS: Mutex<Option<HashMap<String, String>>> = Mutex::new(None);

/// Notes the kind of audio `stream` sends.
pub(crate) fn sends(stream: &str, mime: Option<&str>) {
    let (Ok(mut kinds), Some(mime)) = (KINDS.lock(), mime) else {
        return;
    };
    kinds
        .get_or_insert_with(HashMap::new)
        .insert(stream.to_owned(), mime.to_owned());
}

/// Starts writing what the station on `stream` plays into a new file in `folder` named `stem`,
/// with the extension of the audio it sends. The audio is kept as the station sends it, so
/// nothing is re-encoded. Returns where the file is.
pub fn start_recording(stream: &str, stem: &str, folder: &Path) -> Result<PathBuf> {
    let mime = KINDS
        .lock()
        .ok()
        .and_then(|kinds| kinds.as_ref()?.get(stream).cloned())
        .unwrap_or_default();
    let extension = match mime.as_str() {
        "audio/aac" | "audio/aacp" | "audio/x-aac" | "audio/mp4" => "aac",
        "audio/ogg" | "application/ogg" => "ogg",
        _ => "mp3",
    };
    std::fs::create_dir_all(folder).context("cannot make the recordings folder")?;
    let stem: String = stem
        .chars()
        .map(|c| match c {
            '/' | ':' | '\\' => '-',
            c => c,
        })
        .collect();
    let path = folder.join(format!("{}.{extension}", stem.trim()));
    let file = File::create(&path).context("cannot make the recording file")?;
    if let Ok(mut recording) = RECORDING.lock() {
        *recording = Some(Recording {
            stream: stream.to_owned(),
            file,
            path: path.clone(),
        });
    }
    Ok(path)
}

/// Stops the recording and returns where it was saved.
pub fn stop_recording() -> Option<PathBuf> {
    let recording = RECORDING.lock().ok()?.take()?;
    Some(recording.path)
}

/// The station being recorded, by stream address.
pub fn recording() -> Option<String> {
    let recording = RECORDING.lock().ok()?;
    recording.as_ref().map(|recording| recording.stream.clone())
}

/// Adds audio read from `stream` to the recording, when that is the station being recorded. A
/// write that fails ends the recording.
pub(crate) fn record(stream: &str, audio: &[u8]) {
    let Ok(mut recording) = RECORDING.lock() else {
        return;
    };
    let Some(current) = recording
        .as_mut()
        .filter(|current| current.stream == stream)
    else {
        return;
    };
    if let Err(error) = current.file.write_all(audio) {
        log::warn!("radio: cannot write the recording: {error}");
        *recording = None;
    }
}

/// The song a station said it is playing, with the stream it said it on. Only the station
/// playing now writes it.
static HEARD: Mutex<Option<(String, String)>> = Mutex::new(None);

/// The song the station on `stream` last said it is playing, when it says.
pub fn now_playing(stream: &str) -> Option<String> {
    let heard = HEARD.lock().ok()?;
    heard
        .as_ref()
        .filter(|(from, _)| from == stream)
        .map(|(_, title)| title.clone())
}

/// Keeps the song title a station sent in its stream's metadata.
pub(crate) fn hear(stream: &str, title: String) {
    if let Ok(mut heard) = HEARD.lock() {
        *heard = Some((stream.to_owned(), title));
    }
}

/// The stream address a station's track id carries.
pub fn stream_of(id: &str) -> Option<&str> {
    id.strip_prefix(RADIO_PREFIX)
}

/// The stations of `country` that play, most listened to first, each listed once. Morocco also
/// gets the stations heard there that Radio Browser files abroad, last; only its own list is
/// required, so those are left out when they cannot be read.
pub async fn stations(http: &reqwest::Client, country: &str) -> Result<Vec<Station>> {
    let url = format!("{STATIONS}{country}");
    let mut listed = match country == HOME_COUNTRY {
        true => {
            let (listed, abroad) = tokio::join!(fetch(http, &url), fetch(http, ABROAD));
            let mut listed = listed?;
            match abroad {
                Ok(abroad) => listed.extend(abroad),
                Err(error) => log::warn!("radio: cannot read the stations abroad: {error:#}"),
            }
            listed
        }
        false => fetch(http, &url).await?,
    };
    listed.retain(|station| !station.name.trim().is_empty());
    Ok(playable(listed))
}

async fn fetch(http: &reqwest::Client, url: &str) -> Result<Vec<Station>> {
    http.get(url)
        .header("User-Agent", AGENT)
        .send()
        .await
        .context("cannot reach radio browser")?
        .error_for_status()
        .context("radio browser refused the station list")?
        .json()
        .await
        .context("cannot read the station list")
}

/// Keeps the stations the decoder can play, and the first of any that share a name or a stream.
/// A duplicate still lends its logo to the station kept when that one has none.
fn playable(listed: Vec<Station>) -> Vec<Station> {
    let mut kept: Vec<Station> = Vec::new();
    let mut seen: HashMap<String, usize> = HashMap::new();
    for station in listed
        .into_iter()
        .filter(Station::playable)
        .map(Station::tidy)
    {
        let keys = [station.name.to_lowercase(), station.stream_key()];
        match keys.iter().find_map(|key| seen.get(key).copied()) {
            Some(index) => {
                if kept[index].favicon.trim().is_empty() {
                    kept[index].favicon = station.favicon;
                }
            }
            None => {
                for key in keys {
                    seen.insert(key, kept.len());
                }
                kept.push(station);
            }
        }
    }
    kept
}
