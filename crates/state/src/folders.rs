//! The library seen as the folders on disk its songs are in, one folder at a time.

use std::collections::BTreeMap;
use std::path::{Path, PathBuf};

use gpui::{Context, Entity};
use music::{LOCAL_TRACK_PREFIX, Track};

use crate::{AppSettings, Library, Shelf};

/// A folder inside the one shown, with how many songs are in it and below it.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Subfolder {
    pub path: PathBuf,
    pub name: String,
    pub songs: usize,
}

/// Which folder is open, the folders in it and the songs right in it. With none open, the
/// music folders from settings are the subfolders and there are no songs.
pub struct Folders {
    library: Entity<Library>,
    settings: Entity<AppSettings>,
    open: Option<PathBuf>,
    subfolders: Vec<Subfolder>,
    tracks: Vec<Track>,
}

impl Folders {
    pub fn new(
        library: Entity<Library>,
        settings: Entity<AppSettings>,
        cx: &mut Context<Self>,
    ) -> Self {
        cx.observe(&library, |this, _, cx| this.refresh(cx))
            .detach();
        cx.observe(&settings, |this, _, cx| this.refresh(cx))
            .detach();
        let mut folders = Self {
            library,
            settings,
            open: None,
            subfolders: Vec::new(),
            tracks: Vec::new(),
        };
        folders.refresh(cx);
        folders
    }

    /// The folder shown, `None` for the list of music folders.
    pub fn open_folder(&self) -> Option<&Path> {
        self.open.as_deref()
    }

    pub fn subfolders(&self) -> &[Subfolder] {
        &self.subfolders
    }

    /// The songs right in the folder shown, not in its subfolders.
    pub fn tracks(&self) -> &[Track] {
        &self.tracks
    }

    /// Shows `folder`, or the music folders for `None`.
    pub fn open(&mut self, folder: Option<PathBuf>, cx: &mut Context<Self>) {
        self.open = folder;
        self.refresh(cx);
    }

    /// Shows the folder holding the one shown, or the music folders from a music folder.
    pub fn up(&mut self, cx: &mut Context<Self>) {
        let Some(open) = self.open.take() else {
            return;
        };
        let roots = self.settings.read(cx).local_folders().to_vec();
        self.open = match roots.contains(&open) {
            true => None,
            false => open.parent().map(Path::to_path_buf),
        };
        self.refresh(cx);
    }

    fn refresh(&mut self, cx: &mut Context<Self>) {
        let library = self.library.read(cx);
        let paths = library
            .state(Shelf::Local)
            .tracks()
            .iter()
            .filter_map(|track| Some((path_of(track)?, track)));

        let mut counts: BTreeMap<PathBuf, usize> = BTreeMap::new();
        let mut tracks = Vec::new();
        match &self.open {
            None => {
                let roots = self.settings.read(cx).local_folders().to_vec();
                for (path, _) in paths {
                    if let Some(root) = roots.iter().find(|root| path.starts_with(root)) {
                        *counts.entry(root.clone()).or_default() += 1;
                    }
                }
                for root in roots {
                    counts.entry(root).or_default();
                }
            }
            Some(open) => {
                for (path, track) in paths {
                    let Ok(rest) = path.strip_prefix(open) else {
                        continue;
                    };
                    let mut parts = rest.components();
                    let first = parts.next();
                    match (first, parts.next()) {
                        (Some(_), None) => tracks.push(track.clone()),
                        (Some(first), Some(_)) => {
                            *counts.entry(open.join(first)).or_default() += 1;
                        }
                        (None, _) => {}
                    }
                }
            }
        }

        self.subfolders = counts
            .into_iter()
            .map(|(path, songs)| Subfolder {
                name: path
                    .file_name()
                    .map(|name| name.to_string_lossy().into_owned())
                    .unwrap_or_else(|| path.display().to_string()),
                path,
                songs,
            })
            .collect();
        self.tracks = tracks;
        cx.notify();
    }
}

/// The file a local song is, from its id.
fn path_of(track: &Track) -> Option<&Path> {
    track
        .id
        .as_deref()?
        .strip_prefix(LOCAL_TRACK_PREFIX)
        .map(Path::new)
}
