use std::time::Duration;

use gpui::accesskit::Role;
use gpui::prelude::*;
use gpui::{AnyElement, App, Context, MouseUpEvent, SharedString, div};
use i18n::t;
use state::Sleep;
use ui::{ActiveTheme as _, Scrubber, Switch};

use super::rows::segmented;
use super::{Group, SettingsView, Slot};

/// How wide the sleep slider and the time beside it stand, in covers.
const SLEEP_WIDTH: f32 = 1.;
const SLEEP_LABEL: f32 = 0.7;
/// The shortest and longest timer the slider sets, in minutes.
const SLEEP_MIN_MINUTES: u64 = 1;
const SLEEP_MAX_MINUTES: u64 = 120;
const SLEEP_MAGNETS: [u64; 4] = [15, 30, 45, 60];
const SLEEP_MAGNET_WEIGHT: usize = 4;
/// The slot at the far right of the slider, end of track. Off is slot 0 and every minute
/// between takes one slot, or `SLEEP_MAGNET_WEIGHT` for a magnet.
const SLEEP_LAST: usize = (SLEEP_MAX_MINUTES - SLEEP_MIN_MINUTES + 1) as usize
    + SLEEP_MAGNETS.len() * (SLEEP_MAGNET_WEIGHT - 1)
    + 1;

impl SettingsView {
    pub(super) fn playback_groups(&self, _cx: &App) -> Vec<Group> {
        vec![
            Group::new("settings-group-playing", "icons/play.svg").rows([
                Slot::Normalisation,
                Slot::Gapless,
                Slot::Crossfade,
                Slot::Sleep,
                Slot::StayAwake,
            ]),
        ]
    }

    pub(super) fn playback_row(&self, cx: &mut Context<Self>) -> AnyElement {
        let on = self.playback.read(cx).normalisation();

        self.row(
            t!("settings-normalisation"),
            Switch::new("normalisation", on)
                .on_click(cx.listener(move |this, _, _, cx| {
                    this.playback
                        .update(cx, |playback, cx| playback.set_normalisation(!on, cx));
                }))
                .into_any_element(),
        )
    }

    pub(super) fn gapless_row(&self, cx: &mut Context<Self>) -> AnyElement {
        let on = self.playback.read(cx).gapless();

        self.row(
            t!("settings-gapless"),
            Switch::new("gapless", on)
                .on_click(cx.listener(move |this, _, _, cx| {
                    this.playback
                        .update(cx, |playback, cx| playback.set_gapless(!on, cx));
                }))
                .into_any_element(),
        )
    }

    /// How long one track fades into the next, picked from a few lengths with Off first.
    pub(super) fn crossfade_row(&self, cx: &mut Context<Self>) -> AnyElement {
        let current = self.playback.read(cx).crossfade();
        let choices = segmented(
            "crossfade",
            (0..=state::CROSSFADE_MAX).step_by(2).map(|seconds| {
                let label = match seconds {
                    0 => t!("settings-crossfade-off"),
                    seconds => t!("settings-crossfade-seconds", count = seconds),
                };
                (seconds, label)
            }),
            current,
            cx.listener(|this, seconds: &u32, _, cx| {
                let seconds = *seconds;
                this.playback
                    .update(cx, |playback, cx| playback.set_crossfade(seconds, cx));
            }),
        );

        self.row(t!("settings-crossfade"), choices.into_any_element())
    }

    pub(super) fn stay_awake_row(&self, cx: &mut Context<Self>) -> AnyElement {
        let on = self.settings.read(cx).stay_awake();

        self.row(
            t!("settings-stay-awake"),
            Switch::new("stay-awake", on)
                .on_click(cx.listener(move |this, _, _, cx| {
                    this.settings
                        .update(cx, |settings, cx| settings.set_stay_awake(!on, cx));
                }))
                .into_any_element(),
        )
    }

    /// The sleep timer as a slider in its row with the time it is set to before it: off at the
    /// left, end of track at the right, minutes in between with the quarter hours widened. The
    /// label follows a drag at once, and the timer is armed when the drag lets go.
    pub(super) fn sleep_row(&self, cx: &mut Context<Self>) -> AnyElement {
        let theme = *cx.theme();
        let current = self
            .pending_sleep
            .unwrap_or_else(|| self.playback.read(cx).sleep());

        let control = div()
            .flex()
            .items_center()
            .gap_3()
            .child(
                div()
                    .flex_none()
                    .w(theme.metrics.cover * SLEEP_LABEL)
                    .whitespace_nowrap()
                    .text_right()
                    .text_color(match current {
                        Some(_) => theme.foreground,
                        None => theme.muted_foreground,
                    })
                    .child(sleep_label(current)),
            )
            .child(
                div()
                    .id("settings-sleep-slider")
                    .role(Role::Group)
                    .aria_label(t!("settings-sleep"))
                    .w(theme.metrics.cover * SLEEP_WIDTH)
                    .child(
                        Scrubber::new(&self.sleep, sleep_slot(current) as f32 / SLEEP_LAST as f32)
                            .colors(
                                theme.progress_bar,
                                theme.muted_foreground.opacity(0.3),
                                theme.foreground,
                            )
                            .on_move(cx.listener(|this, fraction: &f32, _, cx| {
                                this.pending_sleep = Some(sleep_at_fraction(*fraction));
                                cx.notify();
                            }))
                            .on_release(cx.listener(|this, _: &MouseUpEvent, _, cx| {
                                let Some(sleep) = this.pending_sleep.take() else {
                                    return;
                                };
                                this.playback
                                    .update(cx, |playback, cx| playback.set_sleep(sleep, cx));
                            })),
                    ),
            );

        self.row(t!("settings-sleep"), control.into_any_element())
    }
}

fn sleep_slot(sleep: Option<Sleep>) -> usize {
    match sleep {
        None => 0,
        Some(Sleep::EndOfTrack) => SLEEP_LAST,
        Some(Sleep::After(after)) => minute_slot(after.as_secs() / 60),
    }
}

fn sleep_at_fraction(fraction: f32) -> Option<Sleep> {
    let slot = (fraction.clamp(0., 1.) * SLEEP_LAST as f32).round() as usize;
    match slot {
        0 => None,
        SLEEP_LAST => Some(Sleep::EndOfTrack),
        slot => Some(Sleep::After(Duration::from_secs(slot_minute(slot) * 60))),
    }
}

fn minute_slot(minutes: u64) -> usize {
    let minutes = minutes.clamp(SLEEP_MIN_MINUTES, SLEEP_MAX_MINUTES);
    let earlier_magnets = SLEEP_MAGNETS
        .iter()
        .filter(|magnet| **magnet < minutes)
        .count();
    let width = match SLEEP_MAGNETS.contains(&minutes) {
        true => SLEEP_MAGNET_WEIGHT,
        false => 1,
    };
    (minutes - SLEEP_MIN_MINUTES + 1) as usize
        + earlier_magnets * (SLEEP_MAGNET_WEIGHT - 1)
        + (width - 1) / 2
}

fn slot_minute(slot: usize) -> u64 {
    let mut first = 1;
    for minute in SLEEP_MIN_MINUTES..=SLEEP_MAX_MINUTES {
        let width = match SLEEP_MAGNETS.contains(&minute) {
            true => SLEEP_MAGNET_WEIGHT,
            false => 1,
        };
        if slot < first + width {
            return minute;
        }
        first += width;
    }
    SLEEP_MAX_MINUTES
}

fn sleep_label(sleep: Option<Sleep>) -> SharedString {
    match sleep {
        Some(Sleep::EndOfTrack) => t!("settings-sleep-end-of-track"),
        Some(Sleep::After(after)) => t!("settings-sleep-minutes", count = after.as_secs() / 60),
        None => t!("settings-sleep-off"),
    }
}
