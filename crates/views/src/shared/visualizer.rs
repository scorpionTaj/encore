use std::cell::RefCell;
use std::rc::Rc;
use std::time::{Duration, Instant};

use gpui::{App, EntityId, Window};
use music::Spectrum;
use ui::Levels;

/// How long the drawn levels take to close 63% of the gap to the analyzer's. A time constant
/// rather than a share per frame, so the motion is the same on a 60 Hz and a 144 Hz display
/// and a frame that came late moves further instead of falling behind.
const SETTLE: Duration = Duration::from_millis(40);
/// The longest gap between two frames that still counts as one step: past it the levels adopt
/// the target outright, so coming back from a stalled window snaps rather than swoops.
const STALL: Duration = Duration::from_millis(250);
/// How close the drawn levels must sit to the analyzer's before the frame chain rests. A paused
/// or silent track gets there, and holding the window at display rate for a still picture
/// would only spend battery. [VisualizerDrive::show] re-arms the chain when the levels move.
const REST: f32 = 0.001;

#[derive(Default)]
struct State {
    shown: Levels,
    spectrum: Spectrum,
    armed: bool,
    visible: bool,
    beat: Option<Instant>,
}

#[derive(Clone, Default)]
pub struct VisualizerDrive {
    state: Rc<RefCell<State>>,
}

impl VisualizerDrive {
    pub fn levels(&self) -> Levels {
        self.state.borrow().shown.clone()
    }

    pub fn show(&self, watch: EntityId, spectrum: Spectrum, window: &mut Window) {
        let mut state = self.state.borrow_mut();
        state.visible = true;
        state.spectrum = spectrum;
        if state.armed {
            return;
        }
        state.armed = true;
        state.beat = None;
        drop(state);

        let drive = self.clone();
        window.on_next_frame(move |window, cx| drive.step(watch, window, cx));
    }

    pub fn hide(&self) {
        self.state.borrow_mut().visible = false;
    }

    fn step(&self, watch: EntityId, window: &mut Window, cx: &mut App) {
        let settled = {
            let mut state = self.state.borrow_mut();
            if !state.visible {
                state.armed = false;
                return;
            }
            let spectrum = state.spectrum.clone();
            let now = Instant::now();
            let rate = match state.beat.replace(now) {
                Some(beat) if now.duration_since(beat) < STALL => {
                    1. - (-now.duration_since(beat).as_secs_f32() / SETTLE.as_secs_f32()).exp()
                }
                _ => 1.,
            };
            ease(&mut state.shown.left, spectrum.left(), rate);
            ease(&mut state.shown.right, spectrum.right(), rate);
            let at_rest = gap(&state.shown.left, spectrum.left()) < REST
                && gap(&state.shown.right, spectrum.right()) < REST;
            if at_rest {
                state.armed = false;
            }
            at_rest
        };
        if settled {
            return;
        }

        cx.notify(watch);
        let drive = self.clone();
        window.on_next_frame(move |window, cx| drive.step(watch, window, cx));
    }
}

/// Walks `shown` a share of the way towards `target`, adopting it outright when the band
/// count changes.
fn ease(shown: &mut Vec<f32>, target: Vec<f32>, rate: f32) {
    if shown.len() != target.len() {
        *shown = target;
        return;
    }
    for (shown, target) in shown.iter_mut().zip(&target) {
        *shown += (target - *shown) * rate;
    }
}

/// The worst band's distance from where it should be.
fn gap(shown: &[f32], target: Vec<f32>) -> f32 {
    shown
        .iter()
        .zip(&target)
        .map(|(shown, target)| (target - shown).abs())
        .fold(0., f32::max)
}
