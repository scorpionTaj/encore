use gpui::accesskit::Role;
use gpui::prelude::*;
use gpui::{
    AnyElement, App, Context, Entity, Pixels, Render, ScrollHandle, SharedString, Window, div,
};
use i18n::t;
use music::Track;
use state::{Encore, Playback, SmartPlaylists};
use ui::ActiveTheme as _;
use ui::{
    Button, Listing as _, MenuItem, Picker, Popovers, Scrollbar, Scroller, TableDelegate,
    TableEvent, TableState, runtime, table, vacant,
};

use crate::chrome::{Searchable, Toolbar, Tooled};
use crate::shared::cells;
use crate::shared::hero::{HeroMetaStrip, PageHero};
use crate::shared::page;
use crate::shared::smart_editor::SmartEditor;
use crate::shared::tracks::{LIBRARY_COLUMNS, TrackSource, Tracks};

/// The popover key of the playlist picker.
const PLAYLIST_PICKER: &str = "smart-playlists";

struct SmartTracks(Entity<SmartPlaylists>);

impl Tracks for SmartTracks {
    fn tracks<'a>(&self, cx: &'a App) -> &'a [Track] {
        self.0.read(cx).tracks()
    }

    fn is_loading(&self, _cx: &App) -> bool {
        false
    }
}

/// One smart playlist at a time: the songs that pass its rules now, with a picker for the
/// others and buttons to make a new one or change this one.
pub(crate) struct SmartView {
    smart: Entity<SmartPlaylists>,
    playback: Entity<Playback>,
    width: Pixels,
    scrollbar: Entity<Scrollbar>,
    table: Entity<TableState<TrackSource>>,
    toolbar: Entity<Toolbar>,
    popovers: Popovers,
}

impl SmartView {
    pub(crate) fn new(
        smart: Entity<SmartPlaylists>,
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
                SmartTracks(smart.clone()),
                playback.clone(),
                menu,
                cx,
            )
            .with_liked(Encore::global(cx).library.clone())
            .table(cx.weak_entity());
            TableState::new(TableDelegate::new(source, width, cx), cx).follow(scroll)
        });

        cx.observe(&smart, |this, _, cx| {
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
            smart,
            playback,
            width,
            scrollbar,
            table,
            toolbar,
            popovers: Popovers::default(),
        }
    }

    fn header(&self, cx: &mut Context<Self>) -> AnyElement {
        let smart = self.smart.read(cx);
        let open = smart.open_playlist(cx);
        let playlists = smart.playlists(cx).to_vec();
        let tracks = smart.tracks();
        let duration: std::time::Duration = tracks.iter().map(|track| track.duration).sum();
        let count = tracks.len();

        let title = open
            .as_ref()
            .map(|playlist| SharedString::from(playlist.name.clone()))
            .unwrap_or_else(|| t!("nav-smart"));
        let items = playlists.iter().map(|playlist| {
            let smart = self.smart.clone();
            let id = playlist.id.clone();
            MenuItem::new(
                SharedString::from(format!("smart-{}", playlist.id)),
                SharedString::from(playlist.name.clone()),
            )
            .selected(open.as_ref().is_some_and(|open| open.id == playlist.id))
            .on_click(move |_, _, cx| {
                smart.update(cx, |smart, cx| smart.open(&id, cx));
            })
        });
        let edited = open.clone();

        PageHero::new("smart-hero", title.clone())
            .fallback("icons/list-plus.svg")
            .accent()
            .eyebrow(t!("nav-smart"))
            .when(open.is_some(), |hero| {
                hero.meta(
                    HeroMetaStrip::new()
                        .text(t!("count-songs", count = count))
                        .text(runtime(duration)),
                )
            })
            .actions(
                div()
                    .flex()
                    .items_center()
                    .gap_2()
                    .when(playlists.len() > 1, |this| {
                        this.child(
                            Picker::new(PLAYLIST_PICKER, &self.popovers, title)
                                .width(Picker::REGULAR)
                                .tooltip("smart-pick")
                                .items(items),
                        )
                    })
                    .child(
                        Button::new("smart-new")
                            .outline()
                            .icon("icons/plus.svg")
                            .label(t!("smart-new"))
                            .on_click(|_, window, cx| SmartEditor::open(None, window, cx)),
                    )
                    .when_some(edited, |this, playlist| {
                        this.child(
                            Button::new("smart-edit")
                                .outline()
                                .icon("icons/pencil.svg")
                                .label(t!("smart-edit"))
                                .on_click(move |_, window, cx| {
                                    SmartEditor::open(Some(playlist.clone()), window, cx)
                                }),
                        )
                    }),
            )
            .into_any_element()
    }
}

impl Render for SmartView {
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

        let note = {
            let smart = self.smart.read(cx);
            match smart.open_playlist(cx) {
                None => Some(t!("smart-none")),
                Some(_) if smart.tracks().is_empty() => Some(t!("smart-empty")),
                Some(_) => None,
            }
        };
        let page = Scroller::new("smart-page", &self.scrollbar)
            .bottom_inset(crate::chrome::Chrome::bottom(cx))
            .pt(inset)
            .pb(inset)
            .child(div().px(inset).child(self.header(cx)))
            .child(table(&self.table))
            .when_some(note, |this, note| {
                this.child(
                    div()
                        .id("smart-note")
                        .role(Role::Status)
                        .aria_label(note.clone())
                        .child(vacant(note, cx)),
                )
            });

        div().size_full().child(page)
    }
}

impl Searchable for SmartView {
    fn search(&mut self, query: &str, cx: &mut Context<Self>) {
        self.table.set_query(query, cx);
        cx.notify();
    }

    fn hint() -> SharedString {
        "filter-library".into()
    }
}

impl Tooled for SmartView {
    fn toolbar(&self) -> Entity<Toolbar> {
        self.toolbar.clone()
    }

    fn tools(&self, _cx: &App) -> Vec<AnyElement> {
        Vec::new()
    }
}
