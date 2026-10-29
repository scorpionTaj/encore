use gpui::accesskit::Role;
use gpui::prelude::*;
use gpui::{
    AnyElement, App, Context, Entity, Pixels, Render, ScrollHandle, SharedString, Window, div, svg,
};
use i18n::t;
use music::Track;
use state::{Encore, Folders, Playback, Subfolder};
use ui::{
    ActiveTheme as _, Button, Listing as _, Scrollbar, Scroller, TableDelegate, TableEvent,
    TableState, Text, table, vacant,
};

use crate::chrome::{Searchable, Toolbar, Tooled};
use crate::shared::cells;
use crate::shared::hero::{HeroMetaStrip, PageHero};
use crate::shared::page;
use crate::shared::tracks::{LIBRARY_COLUMNS, TrackSource, Tracks};

struct FolderTracks(Entity<Folders>);

impl Tracks for FolderTracks {
    fn tracks<'a>(&self, cx: &'a App) -> &'a [Track] {
        self.0.read(cx).tracks()
    }

    fn is_loading(&self, _cx: &App) -> bool {
        false
    }
}

/// The library as folders on disk: the folders inside the one open, then the songs right in it.
/// It opens on the music folders from settings.
pub(crate) struct FoldersView {
    folders: Entity<Folders>,
    playback: Entity<Playback>,
    width: Pixels,
    scrollbar: Entity<Scrollbar>,
    table: Entity<TableState<TrackSource>>,
    toolbar: Entity<Toolbar>,
}

impl FoldersView {
    pub(crate) fn new(
        folders: Entity<Folders>,
        playback: Entity<Playback>,
        window: &mut Window,
        cx: &mut Context<Self>,
    ) -> Self {
        let width = cells::content_width(window, Pixels::ZERO, cx);
        let id = cx.entity_id();
        let scrollbar = cx.new(|_| Scrollbar::new(ScrollHandle::new()).watching(id));
        let scroll = scrollbar.read(cx).scroll().clone();
        let table = cx.new(|cx| {
            let menu = cx.new(|_| Scrollbar::inset().watching(id));
            let source = TrackSource::new(
                LIBRARY_COLUMNS,
                FolderTracks(folders.clone()),
                playback.clone(),
                menu,
                cx,
            )
            .with_liked(Encore::global(cx).library.clone())
            .table(cx.weak_entity());
            TableState::new(TableDelegate::new(source, width, cx), cx).follow(scroll)
        });

        cx.observe(&folders, |this, _, cx| {
            this.table.rebuild(cx);
            cx.notify();
        })
        .detach();
        cx.observe(&playback, |this, _, cx| {
            this.table.refresh(cx);
            cx.notify();
        })
        .detach();
        cx.subscribe(&table, |this, _, event, cx| match event {
            TableEvent::DoubleClicked(display) => {
                page::play(&this.table, &this.playback, *display, cx);
            }
            TableEvent::Activated(display) => {
                page::play_or_toggle(&this.table, &this.playback, *display, cx);
            }
            _ => {}
        })
        .detach();

        let toolbar = Toolbar::searchable(&cx.entity(), cx);
        Self {
            folders,
            playback,
            width,
            scrollbar,
            table,
            toolbar,
        }
    }

    fn header(&self, cx: &mut Context<Self>) -> AnyElement {
        let folders = self.folders.read(cx);
        let open = folders.open_folder().map(|path| path.to_path_buf());
        let title = match &open {
            Some(path) => SharedString::from(
                path.file_name()
                    .map(|name| name.to_string_lossy().into_owned())
                    .unwrap_or_else(|| path.display().to_string()),
            ),
            None => t!("nav-folders"),
        };
        let eyebrow = open
            .as_ref()
            .and_then(|path| path.parent())
            .map(|parent| SharedString::from(parent.display().to_string()))
            .unwrap_or_else(|| t!("nav-local"));
        let meta = HeroMetaStrip::new()
            .text(t!("count-folders", count = folders.subfolders().len()))
            .text(t!("count-songs", count = folders.tracks().len()));

        PageHero::new("folders-hero", title)
            .fallback("icons/folder.svg")
            .accent()
            .eyebrow(eyebrow)
            .meta(meta)
            .when(open.is_some(), |hero| {
                hero.actions(
                    div().flex().items_center().child(
                        Button::new("folders-up")
                            .outline()
                            .icon("icons/chevron-up.svg")
                            .label(t!("folders-up"))
                            .on_click(cx.listener(|this, _, _, cx| {
                                this.folders.update(cx, |folders, cx| folders.up(cx));
                            })),
                    ),
                )
            })
            .into_any_element()
    }

    /// The folders inside the one open, each opening on a click.
    fn subfolders(&self, cx: &mut Context<Self>) -> Option<AnyElement> {
        let subfolders = self.folders.read(cx).subfolders().to_vec();
        if subfolders.is_empty() {
            return None;
        }
        let count = subfolders.len();
        let rows = subfolders
            .into_iter()
            .enumerate()
            .map(|(index, folder)| self.subfolder(index, count, folder, cx));
        Some(
            div()
                .id("folders-list")
                .role(Role::List)
                .aria_label(t!("nav-folders"))
                .flex()
                .flex_col()
                .gap_1()
                .children(rows)
                .into_any_element(),
        )
    }

    fn subfolder(
        &self,
        index: usize,
        count: usize,
        folder: Subfolder,
        cx: &mut Context<Self>,
    ) -> AnyElement {
        let theme = cx.theme();
        let songs = t!("count-songs", count = folder.songs);
        let path = folder.path.clone();
        div()
            .id(("folder", index))
            .role(Role::Link)
            .aria_label(SharedString::from(format!("{}, {songs}", folder.name)))
            .aria_position_in_set(index + 1)
            .aria_size_of_set(count)
            .flex()
            .items_center()
            .gap_3()
            .px_2()
            .py_2()
            .rounded(theme.radius)
            .cursor_pointer()
            .hover(|style| style.bg(theme.muted.opacity(0.5)))
            .child(
                svg()
                    .path(icons::path("icons/folder.svg"))
                    .size_5()
                    .flex_none()
                    .text_color(theme.primary),
            )
            .child(
                div()
                    .flex_1()
                    .min_w_0()
                    .truncate()
                    .child(SharedString::from(folder.name)),
            )
            .child(
                div()
                    .flex_none()
                    .text_size(theme.text(Text::Small))
                    .text_color(theme.muted_foreground)
                    .child(songs),
            )
            .on_click(cx.listener(move |this, _, _, cx| {
                let path = path.clone();
                this.folders
                    .update(cx, |folders, cx| folders.open(Some(path), cx));
            }))
            .into_any_element()
    }
}

impl Render for FoldersView {
    fn render(&mut self, window: &mut Window, cx: &mut Context<Self>) -> impl IntoElement {
        self.table.claim(cx);
        let inset = cx.theme().metrics.inset;
        let width = cells::content_width(window, Pixels::ZERO, cx);
        if (width - self.width).abs() >= gpui::px(0.5) {
            self.width = width;
            self.table.set_width(width, cx);
        }

        let scroll = self.scrollbar.read(cx).scroll().clone();
        let viewport = page::viewport(&scroll, inset, window);
        self.table
            .update(cx, |table, _| table.set_viewport(viewport));

        let empty = {
            let folders = self.folders.read(cx);
            folders.subfolders().is_empty() && folders.tracks().is_empty()
        };
        let subfolders = self.subfolders(cx);
        let page = Scroller::new("folders-page", &self.scrollbar)
            .bottom_inset(crate::chrome::Chrome::bottom(cx))
            .pt(inset)
            .pb(inset)
            .child(div().px(inset).child(self.header(cx)))
            .when_some(subfolders, |this, list| {
                this.child(div().px(inset).pt(inset).child(list))
            })
            .child(table(&self.table))
            .when(empty, |this| {
                this.child(
                    div()
                        .id("folders-empty")
                        .role(Role::Status)
                        .aria_label(t!("folders-empty"))
                        .child(vacant(t!("folders-empty"), cx)),
                )
            });

        div().size_full().child(page)
    }
}

impl Searchable for FoldersView {
    fn search(&mut self, query: &str, cx: &mut Context<Self>) {
        self.table.set_query(query, cx);
        cx.notify();
    }

    fn hint() -> SharedString {
        "filter-library".into()
    }
}

impl Tooled for FoldersView {
    fn toolbar(&self) -> Entity<Toolbar> {
        self.toolbar.clone()
    }

    fn tools(&self, _cx: &App) -> Vec<AnyElement> {
        Vec::new()
    }
}
