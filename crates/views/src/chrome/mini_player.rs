use gpui::accesskit::Role;
use gpui::prelude::*;
use gpui::{Context, Entity, FontWeight, Pixels, Render, SharedString, Window, div, px, relative};
use i18n::t;
use state::{Encore, Playback, Queue};
use ui::{ActiveTheme as _, Artwork, Text};

use crate::shared::transport::{fraction, transport};

/// The cover's side in the mini player.
const COVER: Pixels = px(64.);
/// The progress line's height under the controls.
const PROGRESS: Pixels = px(3.);

/// The small always-on-top window: the cover, the song and its artist, the transport controls
/// and a line that shows how far the song has played.
pub struct MiniPlayer {
    playback: Entity<Playback>,
    queue: Entity<Queue>,
}

impl MiniPlayer {
    pub fn new(cx: &mut Context<Self>) -> Self {
        let encore = Encore::global(cx);
        let playback = encore.playback.clone();
        let queue = encore.queue.clone();
        cx.observe(&playback, |_, _, cx| cx.notify()).detach();
        cx.observe(&queue, |_, _, cx| cx.notify()).detach();
        Self { playback, queue }
    }
}

impl Render for MiniPlayer {
    fn render(&mut self, window: &mut Window, cx: &mut Context<Self>) -> impl IntoElement {
        let theme = *cx.theme();
        window.set_rem_size(theme.font_size);
        let playback = self.playback.read(cx);
        let track = playback.shown_track(cx);
        let (elapsed, total) = playback.shown_time(cx);
        let done = fraction(elapsed, total);
        let (title, artist) = match &track {
            Some(track) => (
                SharedString::from(track.name.clone()),
                SharedString::from(track.artists.clone()),
            ),
            None => (t!("player-nothing-playing"), SharedString::default()),
        };
        let cover = track.as_ref().and_then(|track| track.cover.clone());

        div()
            .id("mini-player")
            .role(Role::Group)
            .aria_label(t!("mini-player"))
            .size_full()
            .flex()
            .flex_col()
            .justify_between()
            .bg(theme.background)
            .text_color(theme.foreground)
            .child(
                div()
                    .flex()
                    .items_center()
                    .gap_3()
                    .px(theme.metrics.pad)
                    .pt(theme.metrics.title_bar)
                    .child(
                        Artwork::new(cover)
                            .size(COVER)
                            .fallback("icons/music.svg")
                            .corner_radius(theme.radius),
                    )
                    .child(
                        div()
                            .flex()
                            .flex_col()
                            .flex_1()
                            .min_w_0()
                            .gap_1()
                            .child(
                                div()
                                    .id("mini-player-title")
                                    .role(Role::Heading)
                                    .aria_label(title.clone())
                                    .truncate()
                                    .font_weight(FontWeight::SEMIBOLD)
                                    .child(title),
                            )
                            .child(
                                div()
                                    .truncate()
                                    .text_size(theme.text(Text::Small))
                                    .text_color(theme.muted_foreground)
                                    .child(artist),
                            )
                            .child(transport(&self.playback, &self.queue, false, cx)),
                    ),
            )
            .child(
                div()
                    .id("mini-player-progress")
                    .role(Role::ProgressIndicator)
                    .aria_label(t!("mini-player-progress"))
                    .w_full()
                    .h(PROGRESS)
                    .bg(theme.muted)
                    .child(div().h_full().w(relative(done)).bg(theme.progress_bar)),
            )
    }
}
