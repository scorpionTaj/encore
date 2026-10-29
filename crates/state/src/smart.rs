//! Smart playlists: saved rules over the library whose songs follow the library as it changes.
//! The rules live in `settings.json`; the songs are found again whenever the library moves.

use std::time::{Duration, SystemTime, UNIX_EPOCH};

use gpui::{Context, Entity};
use music::{Encoding, Track};
use serde::{Deserialize, Serialize};

use crate::{AppSettings, Library, Shelf};

/// What a rule looks at in a song.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "kebab-case")]
pub enum RuleField {
    Title,
    Artist,
    Album,
    Genre,
    /// Whether the file is lossless; the value is ignored.
    Lossless,
    /// The length in minutes.
    Minutes,
    /// How many days ago the song was added.
    AddedDays,
    /// How many days ago the file was last changed.
    ModifiedDays,
}

impl RuleField {
    pub const ALL: [Self; 8] = [
        Self::Title,
        Self::Artist,
        Self::Album,
        Self::Genre,
        Self::Lossless,
        Self::Minutes,
        Self::AddedDays,
        Self::ModifiedDays,
    ];

    /// The Fluent key of the field's name.
    pub fn key(self) -> &'static str {
        match self {
            Self::Title => "smart-field-title",
            Self::Artist => "smart-field-artist",
            Self::Album => "smart-field-album",
            Self::Genre => "smart-field-genre",
            Self::Lossless => "smart-field-lossless",
            Self::Minutes => "smart-field-minutes",
            Self::AddedDays => "smart-field-added",
            Self::ModifiedDays => "smart-field-modified",
        }
    }

    /// Whether the field compares numbers rather than text.
    pub fn numeric(self) -> bool {
        matches!(self, Self::Minutes | Self::AddedDays | Self::ModifiedDays)
    }

    /// The comparisons the field offers.
    pub fn ops(self) -> &'static [RuleOp] {
        match self {
            Self::Lossless => &[RuleOp::Is],
            _ if self.numeric() => &[RuleOp::Less, RuleOp::More],
            _ => &[RuleOp::Contains, RuleOp::Lacks, RuleOp::Is],
        }
    }
}

/// How a rule compares the field with its value.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "kebab-case")]
pub enum RuleOp {
    Contains,
    Lacks,
    Is,
    Less,
    More,
}

impl RuleOp {
    pub fn key(self) -> &'static str {
        match self {
            Self::Contains => "smart-op-contains",
            Self::Lacks => "smart-op-lacks",
            Self::Is => "smart-op-is",
            Self::Less => "smart-op-less",
            Self::More => "smart-op-more",
        }
    }
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct Rule {
    pub field: RuleField,
    pub op: RuleOp,
    #[serde(default)]
    pub value: String,
}

impl Rule {
    pub fn new(field: RuleField) -> Self {
        Self {
            field,
            op: field.ops()[0],
            value: String::new(),
        }
    }

    /// Whether `track` passes the rule. A text rule with no value passes everything.
    fn keeps(&self, track: &Track, now: i64) -> bool {
        let text = |haystack: &str| {
            let needle = self.value.trim().to_lowercase();
            let haystack = haystack.to_lowercase();
            match self.op {
                _ if needle.is_empty() => true,
                RuleOp::Contains => haystack.contains(&needle),
                RuleOp::Lacks => !haystack.contains(&needle),
                RuleOp::Is => haystack == needle,
                RuleOp::Less | RuleOp::More => true,
            }
        };
        let number = |value: Option<f64>| {
            let Ok(limit) = self.value.trim().parse::<f64>() else {
                return true;
            };
            let Some(value) = value else {
                return false;
            };
            match self.op {
                RuleOp::Less => value < limit,
                RuleOp::More => value > limit,
                _ => true,
            }
        };
        let days = |at: Option<i64>| at.map(|at| (now - at).max(0) as f64 / 86_400.);
        match self.field {
            RuleField::Title => text(&track.name),
            RuleField::Artist => text(&track.artists),
            RuleField::Album => text(&track.album),
            RuleField::Genre => text(&track.tags.join(", ")),
            RuleField::Lossless => track.encoding.is_some_and(lossless),
            RuleField::Minutes => number(Some(track.duration.as_secs_f64() / 60.)),
            RuleField::AddedDays => number(days(track.added_at)),
            RuleField::ModifiedDays => number(days(track.modified_at)),
        }
    }
}

/// One saved smart playlist.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct SmartPlaylist {
    pub id: String,
    pub name: String,
    /// Whether a song has to pass every rule, rather than any one of them.
    #[serde(default = "every")]
    pub all: bool,
    #[serde(default)]
    pub rules: Vec<Rule>,
    /// The most songs it holds, the newest first when it holds fewer than match.
    #[serde(default)]
    pub limit: Option<usize>,
}

impl SmartPlaylist {
    pub fn new(name: String) -> Self {
        let id = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis()
            .to_string();
        Self {
            id,
            name,
            all: true,
            rules: vec![Rule::new(RuleField::Artist)],
            limit: None,
        }
    }

    /// The songs of `tracks` that pass the rules, in library order, cut to the limit.
    pub fn matching(&self, tracks: &[Track]) -> Vec<Track> {
        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or(Duration::ZERO)
            .as_secs() as i64;
        let mut kept: Vec<Track> = tracks
            .iter()
            .filter(|track| match self.all {
                true => self.rules.iter().all(|rule| rule.keeps(track, now)),
                false => {
                    self.rules.is_empty() || self.rules.iter().any(|rule| rule.keeps(track, now))
                }
            })
            .cloned()
            .collect();
        if let Some(limit) = self.limit.filter(|limit| *limit > 0 && *limit < kept.len()) {
            kept.sort_by_key(|track| std::cmp::Reverse(track.added_at));
            kept.truncate(limit);
        }
        kept
    }
}

/// The smart playlist open on its page and the songs it holds now.
pub struct SmartPlaylists {
    library: Entity<Library>,
    settings: Entity<AppSettings>,
    open: Option<String>,
    tracks: Vec<Track>,
}

impl SmartPlaylists {
    pub fn new(
        library: Entity<Library>,
        settings: Entity<AppSettings>,
        cx: &mut Context<Self>,
    ) -> Self {
        cx.observe(&library, |this, _, cx| this.refresh(cx))
            .detach();
        cx.observe(&settings, |this, _, cx| this.refresh(cx))
            .detach();
        let mut smart = Self {
            library,
            settings,
            open: None,
            tracks: Vec::new(),
        };
        smart.refresh(cx);
        smart
    }

    /// Every saved smart playlist.
    pub fn playlists<'a>(&self, cx: &'a gpui::App) -> &'a [SmartPlaylist] {
        self.settings.read(cx).smart_playlists()
    }

    /// The smart playlist shown, the first one until another is picked.
    pub fn open_playlist(&self, cx: &gpui::App) -> Option<SmartPlaylist> {
        let playlists = self.playlists(cx);
        self.open
            .as_ref()
            .and_then(|id| playlists.iter().find(|playlist| playlist.id == *id))
            .or_else(|| playlists.first())
            .cloned()
    }

    pub fn tracks(&self) -> &[Track] {
        &self.tracks
    }

    pub fn open(&mut self, id: &str, cx: &mut Context<Self>) {
        self.open = Some(id.to_owned());
        self.refresh(cx);
    }

    /// Saves `playlist`, replacing the one with its id, and shows it.
    pub fn save(&mut self, playlist: SmartPlaylist, cx: &mut Context<Self>) {
        self.open = Some(playlist.id.clone());
        self.settings.update(cx, |settings, cx| {
            settings.save_smart_playlist(playlist, cx)
        });
    }

    pub fn delete(&mut self, id: &str, cx: &mut Context<Self>) {
        self.settings
            .update(cx, |settings, cx| settings.delete_smart_playlist(id, cx));
        self.open = None;
        self.refresh(cx);
    }

    fn refresh(&mut self, cx: &mut Context<Self>) {
        self.tracks = match self.open_playlist(cx) {
            Some(playlist) => playlist.matching(self.library.read(cx).state(Shelf::Local).tracks()),
            None => Vec::new(),
        };
        cx.notify();
    }
}

fn every() -> bool {
    true
}

fn lossless(encoding: Encoding) -> bool {
    matches!(
        encoding,
        Encoding::Flac { .. } | Encoding::Alac { .. } | Encoding::Pcm { .. }
    )
}
