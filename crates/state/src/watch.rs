//! Watches the music folders while the setting is on, so songs added, removed or moved there
//! reach the library without the Rescan button.

use std::path::PathBuf;
use std::time::Duration;

use gpui::{Context, Entity, Task};
use notify::event::ModifyKind;
use notify::{Config, Event, EventKind, RecommendedWatcher, RecursiveMode, Watcher as _};

use crate::{AppSettings, Session};

/// How long the folders have to stay quiet before the rescan starts, so copying an album in
/// scans once rather than once a song.
const SETTLE: Duration = Duration::from_secs(3);
/// How often a change that arrived during a scan checks whether the scan is over.
const WAIT_FOR_SCAN: Duration = Duration::from_secs(2);

/// Follows the music folders on disk and rescans when songs come or go. The rescan is the quick
/// one: only folders whose contents changed are listed again.
pub struct FolderWatch {
    session: Entity<Session>,
    settings: Entity<AppSettings>,
    /// The folders being watched now, empty while the setting is off.
    watched: Vec<PathBuf>,
    watcher: Option<RecommendedWatcher>,
    task: Option<Task<()>>,
}

impl FolderWatch {
    pub fn new(
        session: Entity<Session>,
        settings: Entity<AppSettings>,
        cx: &mut Context<Self>,
    ) -> Self {
        cx.observe(&settings, |this, _, cx| this.sync(cx)).detach();
        let mut watch = Self {
            session,
            settings,
            watched: Vec::new(),
            watcher: None,
            task: None,
        };
        watch.sync(cx);
        watch
    }

    /// Watches the folders the settings name when watching is on, and nothing otherwise. Does
    /// nothing while that has not changed.
    fn sync(&mut self, cx: &mut Context<Self>) {
        let settings = self.settings.read(cx);
        let wanted = match settings.watch_folders() {
            true => settings.local_folders().to_vec(),
            false => Vec::new(),
        };
        if wanted == self.watched {
            return;
        }
        self.watcher = None;
        self.task = None;
        self.watched = wanted;
        if self.watched.is_empty() {
            return;
        }

        let (sender, mut changes) = tokio::sync::mpsc::unbounded_channel();
        let watcher = RecommendedWatcher::new(
            move |result: notify::Result<Event>| match result {
                Ok(event) if touches_songs(&event) => {
                    sender.send(()).ok();
                }
                Ok(_) => {}
                Err(error) => log::warn!("watch: {error}"),
            },
            Config::default(),
        );
        let mut watcher = match watcher {
            Ok(watcher) => watcher,
            Err(error) => {
                log::warn!("watch: cannot watch the music folders: {error}");
                return;
            }
        };
        for folder in &self.watched {
            if let Err(error) = watcher.watch(folder, RecursiveMode::Recursive) {
                log::warn!("watch: cannot watch {}: {error}", folder.display());
            }
        }
        self.watcher = Some(watcher);

        self.task = Some(cx.spawn(async move |this, cx| {
            while changes.recv().await.is_some() {
                // let a copy finish: every further change restarts the wait
                loop {
                    let quiet = cx.background_executor().timer(SETTLE);
                    tokio::pin!(quiet);
                    tokio::select! {
                        biased;
                        more = changes.recv() => {
                            if more.is_none() {
                                return;
                            }
                        }
                        () = &mut quiet => break,
                    }
                }
                // a scan under way may already be reading the new songs' folder, so the rescan
                // waits for it rather than cutting it short
                loop {
                    let Ok(scanning) = this.update(cx, |this, cx| this.session.read(cx).scanning())
                    else {
                        return;
                    };
                    if !scanning {
                        break;
                    }
                    cx.background_executor().timer(WAIT_FOR_SCAN).await;
                }
                log::info!("watch: the music folders changed, rescanning");
                let rescanned = this.update(cx, |this, cx| {
                    this.session
                        .update(cx, |session, cx| session.rescan_local(false, cx));
                });
                if rescanned.is_err() {
                    return;
                }
            }
        }));
    }
}

/// Whether `event` adds, removes or moves a song or a folder. Edits inside a file, and changes to
/// covers, playlists or anything else the scan does not read, are left alone.
fn touches_songs(event: &Event) -> bool {
    let shaped = matches!(
        event.kind,
        EventKind::Create(_) | EventKind::Remove(_) | EventKind::Modify(ModifyKind::Name(_))
    );
    shaped
        && event
            .paths
            .iter()
            .any(|path| path.extension().is_none() || music::is_audio_file(path))
}
