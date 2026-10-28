//! The hook that lets [`crate::AppSettings::window_rounding`] reach the real window. The AppKit
//! toolbar calls are platform code only `encore` links against, so it installs the closure here
//! once at startup. `views` calls [`apply_window_rounding`] from `Root`'s render whenever the
//! setting changes, so neither crate depends on the other's platform bindings.

use gpui::{App, Global, Window};
use ui::Rounding;

type Apply = Box<dyn Fn(&Window, Rounding)>;

struct RoundedWindowHook(Apply);

impl Global for RoundedWindowHook {}

/// Registers the corner-rounding hook. Rounding is a no-op until this is called.
pub fn install_rounded_window_hook(apply: impl Fn(&Window, Rounding) + 'static, cx: &mut App) {
    cx.set_global(RoundedWindowHook(Box::new(apply)));
}

/// Applies the window-rounding preference to the live window, if a hook is installed.
pub fn apply_window_rounding(window: &Window, rounding: Rounding, cx: &App) {
    if let Some(hook) = cx.try_global::<RoundedWindowHook>() {
        (hook.0)(window, rounding);
    }
}
