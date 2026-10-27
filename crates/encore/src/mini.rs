//! The mini player's window: one at a time, floating above every other app.

use gpui::{
    App, AppContext as _, Bounds, Global, Pixels, Size, TitlebarOptions, WindowBounds,
    WindowHandle, WindowKind, WindowOptions, point, px, size,
};
use ui::ActiveTheme as _;
use views::MiniPlayer;

const SIZE: Size<Pixels> = size(px(380.), px(132.));

/// The open mini player window, so a second toggle closes it and the main window can be told
/// apart from it.
#[derive(Default)]
struct Mini(Option<WindowHandle<MiniPlayer>>);

impl Global for Mini {}

/// Opens the mini player, or closes it when it is open.
pub fn toggle(cx: &mut App) {
    let open = cx.try_global::<Mini>().and_then(|mini| mini.0);
    if let Some(handle) = open {
        cx.set_global(Mini(None));
        if handle
            .update(cx, |_, window, _| window.remove_window())
            .is_ok()
        {
            return;
        }
    }
    let background = ui::backdrop(false, false);
    let opened = cx.open_window(
        WindowOptions {
            window_bounds: Some(WindowBounds::Windowed(Bounds::centered(None, SIZE, cx))),
            titlebar: Some(TitlebarOptions {
                title: Some("Encore".into()),
                appears_transparent: true,
                traffic_light_position: Some(point(px(9.), px(9.))),
            }),
            kind: WindowKind::PopUp,
            is_movable: true,
            is_resizable: false,
            is_minimizable: false,
            window_background: background,
            app_id: Some("encore".into()),
            ..Default::default()
        },
        |window, cx| {
            window.set_rem_size(cx.theme().font_size);
            cx.new(MiniPlayer::new)
        },
    );
    match opened {
        Ok(handle) => cx.set_global(Mini(Some(handle))),
        Err(error) => log::warn!("mini player: cannot open the window: {error:#}"),
    }
}

/// Whether `window` is the mini player's.
pub fn is_mini(window: gpui::AnyWindowHandle, cx: &App) -> bool {
    cx.try_global::<Mini>()
        .and_then(|mini| mini.0)
        .is_some_and(|handle| handle.window_id() == window.window_id())
}
