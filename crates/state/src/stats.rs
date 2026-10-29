//! Listening stats: how much was played over a period, and the songs and artists played most.
//! Read from `listens`, which `History` fills on every play and never trims.

use std::time::{Duration, SystemTime, UNIX_EPOCH};

use anyhow::{Context as _, Result};
use gpui::{Context, Entity, Task};
use rusqlite::params;
use storage::Database;

use crate::{History, Io, join};

/// How many songs and artists each top list holds.
const TOP: usize = 10;
const DAY_MS: i64 = 24 * 60 * 60 * 1000;

/// How far back the stats look.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub enum Period {
    Week,
    #[default]
    Month,
    Year,
    All,
}

impl Period {
    pub const ALL: [Self; 4] = [Self::Week, Self::Month, Self::Year, Self::All];

    /// The Fluent key of the period's name.
    pub fn key(self) -> &'static str {
        match self {
            Self::Week => "stats-week",
            Self::Month => "stats-month",
            Self::Year => "stats-year",
            Self::All => "stats-all",
        }
    }

    /// The first moment counted, in milliseconds since the epoch.
    fn since(self) -> i64 {
        let days = match self {
            Self::Week => 7,
            Self::Month => 30,
            Self::Year => 365,
            Self::All => return 0,
        };
        now() - days * DAY_MS
    }
}

/// A song in a top list.
#[derive(Clone, Debug, PartialEq)]
pub struct TopSong {
    pub id: String,
    pub name: String,
    pub artists: String,
    pub cover: Option<String>,
    pub plays: u32,
}

/// An artist in a top list, with the cover of one of their songs.
#[derive(Clone, Debug, PartialEq)]
pub struct TopArtist {
    pub name: String,
    pub cover: Option<String>,
    pub plays: u32,
}

/// What one period adds up to.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct Summary {
    pub plays: u32,
    pub listened: Duration,
    pub songs: Vec<TopSong>,
    pub artists: Vec<TopArtist>,
}

/// The stats for the period picked on the stats page. They are read when the page opens and
/// again after every play while it is open.
pub struct Stats {
    database: Database,
    io: Io,
    period: Period,
    summary: Option<Summary>,
    shown: bool,
    task: Option<Task<()>>,
}

impl Stats {
    pub fn new(
        history: Entity<History>,
        database: Database,
        io: Io,
        cx: &mut Context<Self>,
    ) -> Self {
        cx.observe(&history, |this, _, cx| {
            if this.shown {
                this.reload(cx);
            }
        })
        .detach();
        Self {
            database,
            io,
            period: Period::default(),
            summary: None,
            shown: false,
            task: None,
        }
    }

    pub fn period(&self) -> Period {
        self.period
    }

    /// The stats for the period, `None` until they have been read.
    pub fn summary(&self) -> Option<&Summary> {
        self.summary.as_ref()
    }

    /// Reads the stats the first time the page opens.
    pub fn ensure(&mut self, cx: &mut Context<Self>) {
        if !self.shown {
            self.shown = true;
            self.reload(cx);
        }
    }

    pub fn set_period(&mut self, period: Period, cx: &mut Context<Self>) {
        if period != self.period {
            self.period = period;
            self.reload(cx);
        }
    }

    fn reload(&mut self, cx: &mut Context<Self>) {
        let database = self.database.clone();
        let since = self.period.since();
        let io = self.io.clone();
        self.task = Some(cx.spawn(async move |this, cx| {
            let read = join(io.spawn_blocking(move || summarize(&database, since))).await;
            this.update(cx, |this, cx| {
                this.task = None;
                match read {
                    Ok(summary) => this.summary = Some(summary),
                    Err(error) => log::warn!("stats: cannot read the listening stats: {error:#}"),
                }
                cx.notify();
            })
            .ok();
        }));
    }
}

/// Adds up the plays since `since`, in milliseconds since the epoch.
fn summarize(database: &Database, since: i64) -> Result<Summary> {
    let connection = database.open().context("cannot open the listening stats")?;
    let (plays, listened): (u32, i64) = connection
        .query_row(
            "SELECT COUNT(*), COALESCE(SUM(duration_ms), 0) FROM listens WHERE played_at >= ?",
            params![since],
            |row| Ok((row.get(0)?, row.get(1)?)),
        )
        .context("cannot count the plays")?;

    let mut query = connection
        .prepare(
            "SELECT track_id, MAX(name), MAX(artists), MAX(cover), COUNT(*) AS plays
             FROM listens WHERE played_at >= ?
             GROUP BY track_id ORDER BY plays DESC, MAX(played_at) DESC LIMIT ?",
        )
        .context("cannot prepare the top songs")?;
    let songs = query
        .query_map(params![since, TOP as i64], |row| {
            Ok(TopSong {
                id: row.get(0)?,
                name: row.get(1)?,
                artists: row.get(2)?,
                cover: row.get(3)?,
                plays: row.get(4)?,
            })
        })
        .context("cannot read the top songs")?
        .collect::<rusqlite::Result<Vec<_>>>()
        .context("cannot read the top songs")?;

    let mut query = connection
        .prepare(
            "SELECT artists, MAX(cover), COUNT(*) AS plays
             FROM listens WHERE played_at >= ? AND artists != ''
             GROUP BY artists ORDER BY plays DESC, MAX(played_at) DESC LIMIT ?",
        )
        .context("cannot prepare the top artists")?;
    let artists = query
        .query_map(params![since, TOP as i64], |row| {
            Ok(TopArtist {
                name: row.get(0)?,
                cover: row.get(1)?,
                plays: row.get(2)?,
            })
        })
        .context("cannot read the top artists")?
        .collect::<rusqlite::Result<Vec<_>>>()
        .context("cannot read the top artists")?;

    Ok(Summary {
        plays,
        listened: Duration::from_millis(listened.max(0) as u64),
        songs,
        artists,
    })
}

fn now() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis()
        .min(i64::MAX as u128) as i64
}
