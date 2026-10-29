//! A local song's `.lrc` file beside it: read into timed lines for the lyrics editor, and
//! written back from them.

use std::fmt::Write as _;
use std::path::PathBuf;
use std::time::Duration;

use anyhow::{Context as _, Result};
use music::{LOCAL_TRACK_PREFIX, Track};

/// One line of lyrics and the moment it starts, when it has one.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct LyricLine {
    pub at: Option<Duration>,
    pub text: String,
}

/// Where the `.lrc` file of a local song is, or would be.
pub fn lrc_path(track: &Track) -> Option<PathBuf> {
    let path = track.id.as_deref()?.strip_prefix(LOCAL_TRACK_PREFIX)?;
    Some(PathBuf::from(path).with_extension("lrc"))
}

/// The lines of the song's `.lrc` file, empty when it has none. Tags such as `[ar:…]` are left
/// out, since the editor only edits the lines.
pub fn read_lrc(track: &Track) -> Vec<LyricLine> {
    let Some(text) = lrc_path(track).and_then(|path| std::fs::read_to_string(path).ok()) else {
        return Vec::new();
    };
    text.lines()
        .filter_map(|line| {
            let line = line.trim_end();
            let Some(rest) = line.strip_prefix('[') else {
                return Some(LyricLine {
                    at: None,
                    text: line.to_owned(),
                });
            };
            let (stamp, text) = rest.split_once(']')?;
            let at = parse_stamp(stamp)?;
            Some(LyricLine {
                at: Some(at),
                text: text.trim().to_owned(),
            })
        })
        .collect()
}

/// Writes `lines` to the song's `.lrc` file, replacing what was there.
pub fn write_lrc(track: &Track, lines: &[LyricLine]) -> Result<PathBuf> {
    let path = lrc_path(track).context("this song has no file to keep lyrics beside")?;
    let mut text = String::new();
    for line in lines {
        if let Some(at) = line.at {
            text.push_str(&stamp(at));
        }
        let _ = writeln!(text, "{}", line.text);
    }
    std::fs::write(&path, text).context("cannot write the lyrics file")?;
    Ok(path)
}

/// A moment as an LRC stamp, `[mm:ss.xx]`.
pub fn stamp(at: Duration) -> String {
    let hundredths = at.as_millis() / 10;
    format!(
        "[{:02}:{:02}.{:02}]",
        hundredths / 6000,
        hundredths / 100 % 60,
        hundredths % 100
    )
}

/// Reads `mm:ss.xx` or `mm:ss`, leaving out tags such as `ar:Artist`.
fn parse_stamp(stamp: &str) -> Option<Duration> {
    let (minutes, seconds) = stamp.split_once(':')?;
    let minutes: u64 = minutes.trim().parse().ok()?;
    let seconds: f64 = seconds.trim().parse().ok()?;
    Some(Duration::from_secs(minutes * 60) + Duration::from_secs_f64(seconds))
}
