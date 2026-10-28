use gpui::{Context, Entity};

use crate::{AppSettings, Io, Playback, PlaybackState};

/// What the platform is asked to keep awake. The display is only wanted along with the system.
#[derive(Clone, Copy, Default, PartialEq, Eq)]
struct Want {
    system: bool,
    display: bool,
}

/// Keeps the system awake while music plays, and the display on while it plays in the focused
/// fullscreen view.
pub struct Wake {
    settings: Entity<AppSettings>,
    playback: Entity<Playback>,
    fullscreen: bool,
    focused: bool,
    applied: Want,
    assertions: Assertions,
}

impl Wake {
    pub fn new(
        settings: Entity<AppSettings>,
        playback: Entity<Playback>,
        io: Io,
        cx: &mut Context<Self>,
    ) -> Self {
        cx.observe(&settings, |this, _, cx| this.apply(cx)).detach();
        cx.observe(&playback, |this, _, cx| this.apply(cx)).detach();

        let this = cx.weak_entity();
        cx.on_window_closed(move |cx, _| {
            if !cx.windows().is_empty() {
                return;
            }
            this.update(cx, |this, cx| {
                this.fullscreen = false;
                this.focused = false;
                this.apply(cx);
            })
            .ok();
        })
        .detach();

        let _ = io;

        Self {
            settings,
            playback,
            fullscreen: false,
            focused: false,
            applied: Want::default(),
            assertions: Assertions::default(),
        }
    }

    /// Records whether the window shows the fullscreen view, which the display lock needs.
    pub fn set_fullscreen(&mut self, on: bool, cx: &mut Context<Self>) {
        self.fullscreen = on;
        self.apply(cx);
    }

    /// Records whether the window has focus, which the display lock needs.
    pub fn set_focused(&mut self, on: bool, cx: &mut Context<Self>) {
        self.focused = on;
        self.apply(cx);
    }

    /// Works out what should be held now and hands it to the platform when it changed. Loading
    /// counts as playing so a track change or a rebuffer does not drop the lock.
    fn apply(&mut self, cx: &mut Context<Self>) {
        let state = self.playback.read(cx).state();
        let playing = self.settings.read(cx).stay_awake()
            && matches!(state, PlaybackState::Playing | PlaybackState::Loading);
        let want = Want {
            system: playing,
            display: playing && self.fullscreen && self.focused,
        };
        if want == self.applied {
            return;
        }
        self.applied = want;
        self.hold(want);
    }

    /// Hands `want` to the power assertions.
    fn hold(&mut self, want: Want) {
        self.assertions.set(want);
    }
}

/// The two power assertions on macOS, each held by its id while it is on.
#[derive(Default)]
struct Assertions {
    system: Option<objc2_io_kit::IOPMAssertionID>,
    display: Option<objc2_io_kit::IOPMAssertionID>,
}

impl Assertions {
    fn set(&mut self, want: Want) {
        hold_one(&mut self.system, want.system, "PreventUserIdleSystemSleep");
        hold_one(
            &mut self.display,
            want.display,
            "PreventUserIdleDisplaySleep",
        );
    }
}

/// Creates or releases one assertion of `kind` so that it is held exactly when `on` is true.
fn hold_one(held: &mut Option<objc2_io_kit::IOPMAssertionID>, on: bool, kind: &str) {
    use objc2_core_foundation::CFString;
    use objc2_io_kit::{
        IOPMAssertionCreateWithName, IOPMAssertionRelease, kIOPMAssertionLevelOn, kIOReturnSuccess,
    };

    if on == held.is_some() {
        return;
    }
    if on {
        let kind = CFString::from_str(kind);
        let name = CFString::from_str(&i18n::t!("wake-reason"));
        let mut id = 0;
        // SAFETY: both strings outlive the call and `id` is a valid out pointer.
        let result = unsafe {
            IOPMAssertionCreateWithName(Some(&kind), kIOPMAssertionLevelOn, Some(&name), &mut id)
        };
        match result == kIOReturnSuccess {
            true => *held = Some(id),
            false => log::warn!("wake: cannot hold a power assertion: {result}"),
        }
    } else if let Some(id) = held.take() {
        let _ = IOPMAssertionRelease(id);
    }
}
