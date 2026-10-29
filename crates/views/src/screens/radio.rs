use std::rc::Rc;

use gpui::accesskit::Role;
use gpui::prelude::*;
use gpui::{
    AnyElement, App, Context, Entity, Pixels, Render, ScrollHandle, SharedString, Window, div,
};
use i18n::t;
use music::radio::{COUNTRIES, Station};
use state::{Playback, Radio, RadioState};
use ui::{ActiveTheme as _, Button, Card, MenuItem, Picker, Popovers, Scrollbar, Scroller, vacant};

use crate::chrome::{Searchable, Toolbar, Tooled};
use crate::screens::library::deck_gap;
use crate::shared::album_grid::CardGrid;
use crate::shared::cells;
use crate::shared::hero::{HeroMetaStrip, PageHero};

/// The popover key of the country picker.
const COUNTRY_PICKER: &str = "radio-country";

/// The radio stations of one country as a grid of cards, favourites first. Pressing a card plays
/// its station, its heart marks it a favourite, and the toolbar search narrows the grid by name
/// and genre.
pub(crate) struct RadioView {
    radio: Entity<Radio>,
    playback: Entity<Playback>,
    scrollbar: Entity<Scrollbar>,
    toolbar: Entity<Toolbar>,
    popovers: Popovers,
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
            popovers: Popovers::default(),
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
        let radio = self.radio.read(cx);
        let loading = matches!(radio.state(), RadioState::Loading);
        let chosen = radio.country(cx).to_owned();
        let countries = COUNTRIES.map(|code| {
            let radio = self.radio.clone();
            MenuItem::new(code, country_name(code))
                .selected(code == chosen)
                .on_click(move |_, _, cx| {
                    radio.update(cx, |radio, cx| radio.set_country(code, cx));
                })
        });
        PageHero::new("radio-hero", t!("nav-radio"))
            .fallback("icons/radio.svg")
            .accent()
            .eyebrow(country_name(&chosen))
            .meta(HeroMetaStrip::new().text(t!("count-stations", count = count)))
            .actions(
                div()
                    .flex()
                    .items_center()
                    .gap_2()
                    .child(
                        Picker::new(COUNTRY_PICKER, &self.popovers, country_name(&chosen))
                            .width(Picker::NARROW)
                            .tooltip("radio-country-pick")
                            .disabled(loading)
                            .items(countries),
                    )
                    .child(
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
        let favorite = self.radio.read(cx).favorite(&station.id, cx);
        let marked = self.radio.clone();
        let id = station.id.clone();
        let heart = Button::new(("radio-favorite", index))
            .ghost()
            .small()
            .icon(match favorite {
                true => "icons/heart-filled.svg",
                false => "icons/heart.svg",
            })
            .tooltip(match favorite {
                true => "radio-favorite-remove",
                false => "radio-favorite-add",
            })
            .on_click(move |_, _, cx| {
                marked.update(cx, |radio, cx| radio.toggle_favorite(&id, cx));
            });
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
        .map(|card| match favorite {
            true => card.trailing(heart),
            false => card.action(heart),
        })
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
        let columns = layout.columns;
        let rows: Vec<CardGrid> = shown
            .chunks(columns)
            .enumerate()
            .map(|(row, stations)| {
                CardGrid::new(width).children(stations.iter().enumerate().map(
                    |(column, station)| self.card(row * columns + column, station, layout.card, cx),
                ))
            })
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
                    .flex()
                    .flex_col()
                    .gap(deck_gap(window))
                    .children(rows),
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

/// A country's name in the interface language, from its ISO 3166 code.
fn country_name(code: &str) -> SharedString {
    i18n::lookup(&format!("country-{}", code.to_ascii_lowercase()), None)
}
