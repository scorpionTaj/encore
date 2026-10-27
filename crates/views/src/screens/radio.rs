use std::rc::Rc;

use gpui::accesskit::Role;
use gpui::prelude::*;
use gpui::{
    AnyElement, App, Context, Entity, Pixels, Render, ScrollHandle, SharedString, Window, div,
};
use i18n::t;
use music::radio::Station;
use state::{Playback, Radio, RadioState};
use ui::{ActiveTheme as _, Button, Card, Scrollbar, Scroller, vacant};

use crate::chrome::{Searchable, Toolbar, Tooled};
use crate::shared::album_grid::CardGrid;
use crate::shared::cells;
use crate::shared::hero::{HeroMetaStrip, PageHero};

/// The Moroccan radio stations as a grid of cards. Pressing a card plays its station, and the
/// toolbar search narrows the grid by name and genre.
pub(crate) struct RadioView {
    radio: Entity<Radio>,
    playback: Entity<Playback>,
    scrollbar: Entity<Scrollbar>,
    toolbar: Entity<Toolbar>,
    /// The search, lowercased and trimmed. Empty shows every station.
    query: String,
}

impl RadioView {
    pub(crate) fn new(
        radio: Entity<Radio>,
        playback: Entity<Playback>,
        cx: &mut Context<Self>,
    ) -> Self {
        cx.observe(&radio, |_, _, cx| cx.notify()).detach();
        cx.observe(&playback, |_, _, cx| cx.notify()).detach();
        let id = cx.entity_id();
        let scrollbar = cx.new(|_| Scrollbar::new(ScrollHandle::new()).watching(id));
        let toolbar = Toolbar::searchable(&cx.entity(), cx);
        Self {
            radio,
            playback,
            scrollbar,
            toolbar,
            query: String::new(),
        }
    }

    /// Reads the station list the first time the page shows.
    pub(crate) fn open(&mut self, cx: &mut Context<Self>) {
        self.radio.update(cx, |radio, cx| radio.ensure(cx));
    }

    /// The stations the search keeps, in the order Radio Browser ranks them.
    fn shown(&self, cx: &App) -> Vec<Station> {
        let stations = self.radio.read(cx).stations();
        stations
            .iter()
            .filter(|station| {
                self.query.is_empty()
                    || station.name.to_lowercase().contains(&self.query)
                    || station.tags.to_lowercase().contains(&self.query)
            })
            .cloned()
            .collect()
    }

    fn header(&self, count: usize, cx: &mut Context<Self>) -> AnyElement {
        let loading = matches!(self.radio.read(cx).state(), RadioState::Loading);
        PageHero::new("radio-hero", t!("nav-radio"))
            .fallback("icons/radio.svg")
            .accent()
            .eyebrow(t!("radio-country"))
            .meta(HeroMetaStrip::new().text(t!("count-stations", count = count)))
            .actions(
                div().flex().items_center().child(
                    Button::new("radio-reload")
                        .outline()
                        .icon("icons/refresh-cw.svg")
                        .label(t!("radio-reload"))
                        .disabled(loading)
                        .on_click(cx.listener(|this, _, _, cx| {
                            this.radio.update(cx, |radio, cx| radio.reload(cx));
                        })),
                ),
            )
            .into_any_element()
    }

    /// One station's card. Its play button shows the station's own state while it is the one on.
    fn card(&self, index: usize, station: &Station, tile: Pixels, cx: &App) -> AnyElement {
        let track = station.track();
        let playback = self.playback.read(cx);
        let current = playback.track().and_then(|playing| playing.id.clone()) == track.id;
        let playing = current && playback.wants_playing();
        let genres = station.genres();
        let played = self.playback.clone();
        let pressed = self.playback.clone();
        let chosen = track.clone();

        Card::new(
            ("radio-station", index),
            SharedString::from(station.name.trim().to_owned()),
        )
        .tile(tile)
        .cover(track.cover.clone())
        .fallback("icons/radio.svg")
        .when(!genres.is_empty(), |card| {
            card.meta(SharedString::from(genres))
        })
        .chosen(current)
        .play(playing, move |_, _, cx| match current {
            true => played.update(cx, |playback, cx| playback.toggle_play(cx)),
            false => played.update(cx, |playback, cx| {
                playback.start(vec![track.clone()], 0, None, cx)
            }),
        })
        .press(move |_, _, cx| {
            pressed.update(cx, |playback, cx| {
                playback.start(vec![chosen.clone()], 0, None, cx)
            })
        })
        .into_any_element()
    }

    /// What the page says in place of cards: why the list did not load, that nothing matches,
    /// or nothing while it is still on its way.
    fn note(&self, count: usize, cx: &App) -> Option<SharedString> {
        match self.radio.read(cx).state() {
            RadioState::Loading => None,
            RadioState::Failed(_) => Some(t!("radio-not-loaded")),
            RadioState::Ready if count > 0 => None,
            RadioState::Ready if !self.query.is_empty() => Some(t!("library-no-matches")),
            RadioState::Ready => Some(t!("radio-empty")),
        }
    }
}

impl Render for RadioView {
    fn render(&mut self, window: &mut Window, cx: &mut Context<Self>) -> impl IntoElement {
        let inset = cx.theme().metrics.inset;
        let width = cells::content_width(window, inset * 2., cx);
        let shown: Rc<Vec<Station>> = Rc::new(self.shown(cx));
        let count = shown.len();
        let layout = CardGrid::layout(width);
        let cards: Vec<AnyElement> = shown
            .iter()
            .enumerate()
            .map(|(index, station)| self.card(index, station, layout.card, cx))
            .collect();
        let note = self.note(count, cx);

        let page = Scroller::new("radio-page", &self.scrollbar)
            .bottom_inset(crate::chrome::Chrome::bottom(cx))
            .pt(inset)
            .pb(inset)
            .child(div().px(inset).child(self.header(count, cx)))
            .child(
                div()
                    .id("radio-stations")
                    .role(Role::List)
                    .aria_label(t!("nav-radio"))
                    .px(inset)
                    .pt(inset)
                    .child(CardGrid::new(width).children(cards)),
            )
            .when_some(note, |this, note| {
                this.child(
                    div()
                        .id("radio-empty")
                        .role(Role::Status)
                        .aria_label(note.clone())
                        .child(vacant(note, cx)),
                )
            });

        div().size_full().child(page)
    }
}

impl Searchable for RadioView {
    fn search(&mut self, query: &str, cx: &mut Context<Self>) {
        self.query = query.trim().to_lowercase();
        cx.notify();
    }

    fn hint() -> SharedString {
        "filter-radio".into()
    }
}

impl Tooled for RadioView {
    fn toolbar(&self) -> Entity<Toolbar> {
        self.toolbar.clone()
    }

    fn tools(&self, _cx: &App) -> Vec<AnyElement> {
        Vec::new()
    }
}
