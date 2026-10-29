use gpui::accesskit::Role;
use gpui::prelude::*;
use gpui::{
    AnyElement, App, Context, Entity, FontWeight, Pixels, Render, ScrollHandle, SharedString,
    Window, div, px,
};
use i18n::t;
use state::{Period, Stats, Summary};
use ui::{
    ActiveTheme as _, Artwork, MenuItem, Picker, Popovers, Scrollbar, Scroller, Text, runtime,
    vacant,
};

use crate::shared::hero::{HeroMetaStrip, PageHero};

/// The popover key of the period picker.
const PERIOD_PICKER: &str = "stats-period";
/// The cover's side on a row of a top list.
const ROW_ART: Pixels = px(40.);

/// What was listened to most over a period: how many songs and for how long, then the top songs
/// and the top artists.
pub(crate) struct StatsView {
    stats: Entity<Stats>,
    scrollbar: Entity<Scrollbar>,
    popovers: Popovers,
}

impl StatsView {
    pub(crate) fn new(stats: Entity<Stats>, cx: &mut Context<Self>) -> Self {
        cx.observe(&stats, |_, _, cx| cx.notify()).detach();
        let id = cx.entity_id();
        let scrollbar = cx.new(|_| Scrollbar::new(ScrollHandle::new()).watching(id));
        Self {
            stats,
            scrollbar,
            popovers: Popovers::default(),
        }
    }

    /// Reads the stats the first time the page shows.
    pub(crate) fn open(&mut self, cx: &mut Context<Self>) {
        self.stats.update(cx, |stats, cx| stats.ensure(cx));
    }

    fn header(&self, summary: Option<&Summary>, cx: &mut Context<Self>) -> AnyElement {
        let chosen = self.stats.read(cx).period();
        let periods = Period::ALL.map(|period| {
            let stats = self.stats.clone();
            MenuItem::new(period.key(), i18n::lookup(period.key(), None))
                .selected(period == chosen)
                .on_click(move |_, _, cx| {
                    stats.update(cx, |stats, cx| stats.set_period(period, cx));
                })
        });
        let meta = summary.map(|summary| {
            HeroMetaStrip::new()
                .text(t!("stats-plays", count = summary.plays))
                .text(runtime(summary.listened))
        });

        PageHero::new("stats-hero", t!("nav-stats"))
            .fallback("icons/chart-column.svg")
            .accent()
            .eyebrow(i18n::lookup(chosen.key(), None))
            .when_some(meta, |hero, meta| hero.meta(meta))
            .actions(
                div().flex().items_center().child(
                    Picker::new(
                        PERIOD_PICKER,
                        &self.popovers,
                        i18n::lookup(chosen.key(), None),
                    )
                    .width(Picker::NARROW)
                    .tooltip("stats-period")
                    .items(periods),
                ),
            )
            .into_any_element()
    }
}

impl Render for StatsView {
    fn render(&mut self, _window: &mut Window, cx: &mut Context<Self>) -> impl IntoElement {
        let inset = cx.theme().metrics.inset;
        let summary = self.stats.read(cx).summary().cloned();
        let header = self.header(summary.as_ref(), cx);
        let empty = summary.as_ref().is_some_and(|summary| summary.plays == 0);

        let lists = summary.filter(|summary| summary.plays > 0).map(|summary| {
            let songs = summary.songs.iter().enumerate().map(|(index, song)| {
                row(
                    ("stats-song", index),
                    index,
                    summary.songs.len(),
                    Place {
                        name: SharedString::from(song.name.clone()),
                        detail: SharedString::from(song.artists.clone()),
                        cover: song.cover.clone(),
                        plays: song.plays,
                        round: false,
                    },
                    cx,
                )
            });
            let artists = summary.artists.iter().enumerate().map(|(index, artist)| {
                row(
                    ("stats-artist", index),
                    index,
                    summary.artists.len(),
                    Place {
                        name: SharedString::from(artist.name.clone()),
                        detail: SharedString::default(),
                        cover: artist.cover.clone(),
                        plays: artist.plays,
                        round: true,
                    },
                    cx,
                )
            });
            div()
                .flex()
                .flex_col()
                .gap(inset)
                .child(section("stats-songs", t!("stats-top-songs"), songs, cx))
                .child(section(
                    "stats-artists",
                    t!("stats-top-artists"),
                    artists,
                    cx,
                ))
        });

        let page = Scroller::new("stats-page", &self.scrollbar)
            .bottom_inset(crate::chrome::Chrome::bottom(cx))
            .pt(inset)
            .pb(inset)
            .child(div().px(inset).child(header))
            .when_some(lists, |this, lists| {
                this.child(div().px(inset).pt(inset).child(lists))
            })
            .when(empty, |this| {
                this.child(
                    div()
                        .id("stats-empty")
                        .role(Role::Status)
                        .aria_label(t!("stats-empty"))
                        .child(vacant(t!("stats-empty"), cx)),
                )
            });

        div().size_full().child(page)
    }
}

/// A titled list of rows.
fn section(
    id: &'static str,
    title: SharedString,
    rows: impl Iterator<Item = AnyElement>,
    cx: &App,
) -> AnyElement {
    let theme = cx.theme();
    div()
        .id(id)
        .role(Role::List)
        .aria_label(title.clone())
        .flex()
        .flex_col()
        .gap_1()
        .child(
            div()
                .pb_2()
                .text_size(theme.text(Text::Large))
                .font_weight(FontWeight::SEMIBOLD)
                .child(title),
        )
        .children(rows)
        .into_any_element()
}

/// What one row of a top list shows.
struct Place {
    name: SharedString,
    /// The line under the name, empty for none.
    detail: SharedString,
    cover: Option<String>,
    plays: u32,
    /// Whether the cover is round, as an artist's is.
    round: bool,
}

/// One place in a top list: its rank, the cover, the name over a second line, and the plays.
fn row(
    id: (&'static str, usize),
    index: usize,
    count: usize,
    place: Place,
    cx: &App,
) -> AnyElement {
    let Place {
        name,
        detail,
        cover,
        plays,
        round,
    } = place;
    let theme = cx.theme();
    let played = t!("stats-plays", count = plays);
    div()
        .id(id)
        .role(Role::ListItem)
        .aria_label(SharedString::from(format!(
            "{} {name}, {played}",
            index + 1
        )))
        .aria_position_in_set(index + 1)
        .aria_size_of_set(count)
        .flex()
        .items_center()
        .gap_3()
        .px_2()
        .py_1()
        .rounded(theme.radius)
        .hover(|style| style.bg(theme.muted.opacity(0.5)))
        .child(
            div()
                .w_6()
                .flex_none()
                .text_right()
                .text_color(theme.muted_foreground)
                .child(SharedString::from((index + 1).to_string())),
        )
        .child(
            Artwork::new(cover)
                .size(ROW_ART)
                .fallback("icons/music.svg")
                .map(|art| match round {
                    true => art.circle(),
                    false => art.corner_radius(theme.radius),
                }),
        )
        .child(
            div()
                .flex()
                .flex_col()
                .flex_1()
                .min_w_0()
                .child(div().truncate().child(name))
                .when(!detail.is_empty(), |this| {
                    this.child(
                        div()
                            .truncate()
                            .text_size(theme.text(Text::Small))
                            .text_color(theme.muted_foreground)
                            .child(detail),
                    )
                }),
        )
        .child(
            div()
                .flex_none()
                .text_size(theme.text(Text::Small))
                .text_color(theme.muted_foreground)
                .child(played),
        )
        .into_any_element()
}
