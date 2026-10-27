use std::path::{Path, PathBuf};
use std::sync::Arc;

use gpui::{Context, Entity, EventEmitter, Task};
use music::{
    Capabilities, Catalog, MusicApi, MusicProvider, PlaybackFactory, PromptSink, ProviderSession,
    Shape, SignIn,
};

use crate::catalog::CatalogSource;
use crate::settings::AppSettings;
use crate::{Io, Outcome, Scan, Shelf, Toast, Toasts, join};

pub enum SessionEvent {
    LocalChanged,
}

/// The local library: the folders it is made of, and the client, catalog and engine the local
/// provider hands out once those are scanned.
pub struct Session {
    settings: Entity<AppSettings>,
    io: Io,
    local_provider: Arc<dyn MusicProvider>,
    local_folders: Vec<PathBuf>,
    local_client: Option<Arc<dyn MusicApi>>,
    local_catalog: Option<Arc<CatalogSource>>,
    local_playback: Option<Arc<dyn PlaybackFactory>>,
    local_capabilities: Capabilities,
    local_task: Option<Task<()>>,
    /// Whether a local scan is under way, so the UI can show its progress.
    scanning: bool,
    /// Whether the scan under way is one the user started, so its end is reported. An automatic
    /// rescan that cancels it takes the report over.
    scan_asked: bool,
}

impl EventEmitter<SessionEvent> for Session {}

impl Session {
    pub fn new(
        local_provider: Arc<dyn MusicProvider>,
        settings: Entity<AppSettings>,
        io: Io,
        cx: &mut Context<Self>,
    ) -> Self {
        let local_folders = settings.read(cx).local_folders().to_vec();
        let local_playback = local_provider.playback_factory();
        let mut session = Self {
            settings,
            io,
            local_provider,
            local_folders,
            local_client: None,
            local_catalog: None,
            local_playback,
            local_capabilities: Capabilities::NONE,
            local_task: None,
            scanning: false,
            scan_asked: false,
        };
        session.restore_local(cx);
        session
    }

    pub fn local_client(&self) -> Option<Arc<dyn MusicApi>> {
        self.local_client.clone()
    }

    /// The client serving a shelf, if that shelf has a provider right now.
    pub fn client_of(&self, shelf: Shelf) -> Option<Arc<dyn MusicApi>> {
        match shelf {
            Shelf::Local => self.local_client.clone(),
        }
    }

    /// What a shelf's library is made of. The local shelf is always a fixed catalog.
    pub fn shape_of(&self, shelf: Shelf) -> Shape {
        match shelf {
            Shelf::Local => Shape::Catalog(Catalog::Fixed),
        }
    }

    pub(crate) fn catalog(&self, _id: &str) -> Option<Arc<CatalogSource>> {
        self.local_catalog.clone()
    }

    pub fn local_playback(&self) -> Option<Arc<dyn PlaybackFactory>> {
        self.local_playback.clone()
    }

    /// The local provider itself, so a file can be read as a track without waiting for
    /// a scan: an explorer open may point outside every configured folder.
    pub fn local_provider(&self) -> Arc<dyn MusicProvider> {
        self.local_provider.clone()
    }

    pub fn local_paths(&self) -> Vec<String> {
        self.local_folders
            .iter()
            .map(|path| path.display().to_string())
            .collect()
    }

    pub fn local_slug(&self) -> &'static str {
        self.local_provider.slug()
    }

    pub fn active_slugs(&self) -> Vec<&'static str> {
        match self.local_client.is_some() {
            true => vec![self.local_slug()],
            false => Vec::new(),
        }
    }

    pub fn slug_for(&self, id: &str) -> Option<&'static str> {
        self.provider_for(id).map(|provider| provider.slug())
    }

    /// The provider an id belongs to. Only local ids have one.
    pub fn provider_for(&self, id: &str) -> Option<&dyn MusicProvider> {
        music::is_local_id(id).then_some(self.local_provider.as_ref())
    }

    /// What the local library can do beyond listing and playing.
    pub fn capabilities_of(&self, shelf: Shelf) -> Capabilities {
        match shelf {
            Shelf::Local => self.local_capabilities,
        }
    }

    fn restore_local(&mut self, cx: &mut Context<Self>) {
        if self.local_folders.is_empty() {
            return;
        }
        self.rescan_local(false, cx);
    }

    /// Adds a folder to the local library, then rescans every configured folder together so
    /// artists and albums that span more than one root merge into one, seamlessly.
    pub fn add_local_folder(&mut self, path: PathBuf, cx: &mut Context<Self>) {
        self.add_local_folders(vec![path], cx);
    }

    /// Same as [`Session::add_local_folder`], for a batch picked in one native dialog.
    pub fn add_local_folders(&mut self, paths: Vec<PathBuf>, cx: &mut Context<Self>) {
        let mut folders = self.local_folders.clone();
        for path in paths {
            if folders.iter().any(|existing| overlaps(existing, &path)) {
                log::warn!(
                    "session: {} overlaps an already-added local folder",
                    path.display()
                );
                continue;
            }
            folders.push(path);
        }
        if folders.len() == self.local_folders.len() {
            return;
        }
        self.set_local_folders(folders, true, cx);
    }

    /// Drops a folder from the local library and offers to put it back.
    pub fn remove_local_folder(&mut self, path: &Path, cx: &mut Context<Self>) {
        let Some(at) = self
            .local_folders
            .iter()
            .position(|existing| existing == path)
        else {
            return;
        };
        let mut folders = self.local_folders.clone();
        let removed = folders.remove(at);
        self.set_local_folders(folders, true, cx);

        let session = cx.entity().downgrade();
        let toast = Toast::new(Outcome::Plain, "toast-folder-removed")
            .name(folder_name(&removed))
            .undo(move |cx| {
                let removed = removed.clone();
                session
                    .update(cx, |session, cx| {
                        session.restore_local_folder(removed, at, cx)
                    })
                    .ok();
            });
        Toasts::post(toast, cx);
    }

    /// Puts a folder the user just removed back where it was in the list. A folder gone from
    /// disk is reported instead, and one that overlaps a folder added since is left alone.
    fn restore_local_folder(&mut self, path: PathBuf, at: usize, cx: &mut Context<Self>) {
        if !path.is_dir() {
            Toasts::about(
                Outcome::Warning,
                "toast-folder-missing",
                folder_name(&path),
                cx,
            );
            return;
        }
        if self
            .local_folders
            .iter()
            .any(|existing| overlaps(existing, &path))
        {
            return;
        }
        let mut folders = self.local_folders.clone();
        folders.insert(at.min(folders.len()), path);
        self.set_local_folders(folders, true, cx);
    }

    /// Rescans every configured local folder without changing the list, e.g. after files
    /// changed on disk or a tag was edited. A `thorough` rescan is the one the user asked for:
    /// it forgets what the last scan recorded, so every folder is listed and every file stat'd
    /// again, which is the only way an edit made behind Encore's back is noticed.
    pub fn rescan_local(&mut self, thorough: bool, cx: &mut Context<Self>) {
        if thorough {
            self.local_provider.forget_scan();
        }
        self.set_local_folders(self.local_folders.clone(), thorough, cx);
    }

    /// Points the local library at `folders` and scans them. A scan already under way is
    /// cancelled first: its folders may be the ones just removed, and two scans would only
    /// fight over the same disk. An `asked` scan is one the user started, and its end or
    /// failure is reported in a toast.
    fn set_local_folders(&mut self, folders: Vec<PathBuf>, asked: bool, cx: &mut Context<Self>) {
        music::progress::cancel();
        self.scan_asked |= asked;
        if folders.is_empty() {
            self.scanning = false;
            self.scan_asked = false;
            self.local_provider.sign_out();
            self.local_folders = Vec::new();
            self.settings.update(cx, |settings, cx| {
                settings.set_local_folders(Vec::new(), cx)
            });
            self.local_client = None;
            self.local_catalog = None;
            self.local_capabilities = Capabilities::NONE;
            self.local_task = None;
            cx.notify();
            cx.emit(SessionEvent::LocalChanged);
            return;
        }

        let provider = self.local_provider.clone();
        let chosen = folders.clone();
        let io = self.io.clone();
        let counting = self.scan_asked;
        self.scanning = true;
        cx.notify();
        self.local_task = Some(cx.spawn(async move |this, cx| {
            let prompt: PromptSink = Arc::new(|_| {});
            let (_tx, rx) = tokio::sync::mpsc::unbounded_channel::<String>();
            let signed_in = join(io.spawn(async move {
                let session = provider.sign_in(SignIn::Path(folders), prompt, rx).await?;
                let songs = match counting {
                    true => session
                        .api
                        .all_tracks()
                        .await
                        .map_or(0, |tracks| tracks.len()),
                    false => 0,
                };
                anyhow::Ok((session, songs))
            }))
            .await;

            this.update(cx, |this, cx| {
                this.scanning = false;
                let asked = std::mem::take(&mut this.scan_asked);
                match signed_in {
                    Ok((session, songs)) => {
                        this.local_folders = chosen.clone();
                        this.settings
                            .update(cx, |settings, cx| settings.set_local_folders(chosen, cx));
                        this.local_signed_in(session, cx);
                        if asked {
                            Scan::global(cx).update(cx, |scan, cx| scan.finished(songs, cx));
                        }
                    }
                    Err(error) => {
                        log::warn!("session: cannot update local music folders: {error:#}");
                        if asked {
                            Toasts::with_detail(
                                Outcome::Failed,
                                "toast-scan-failed",
                                None,
                                format!("{error:#}"),
                                cx,
                            );
                        }
                        cx.notify();
                    }
                }
            })
            .ok();
        }));
    }

    /// Whether a local scan is under way right now.
    pub fn scanning(&self) -> bool {
        self.scanning
    }

    fn local_signed_in(&mut self, session: ProviderSession, cx: &mut Context<Self>) {
        self.local_catalog = Some(Arc::new(CatalogSource::new(session.api.clone())));
        self.local_client = Some(session.api);
        if self.local_playback.is_none() {
            self.local_playback = Some(session.playback);
        }
        self.local_capabilities = session.capabilities;
        cx.notify();
        cx.emit(SessionEvent::LocalChanged);
    }
}

/// The folder's own name for a toast, or the whole path when it has none, such as `/`.
fn folder_name(path: &Path) -> String {
    path.file_name()
        .map(|name| name.to_string_lossy().into_owned())
        .unwrap_or_else(|| path.display().to_string())
}

/// Whether `a` and `b` are the same directory, or one contains the other — either way, scanning
/// both would double-count the tracks they share.
fn overlaps(a: &Path, b: &Path) -> bool {
    let a = std::fs::canonicalize(a).unwrap_or_else(|_| a.to_path_buf());
    let b = std::fs::canonicalize(b).unwrap_or_else(|_| b.to_path_buf());
    a == b || a.starts_with(&b) || b.starts_with(&a)
}
