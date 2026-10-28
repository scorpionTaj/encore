mod actions;
mod assets;
mod cli;
mod dock;
mod gestures;
mod http;
mod logging;
mod memory;
mod mini;
mod single;
mod tray;

use std::path::PathBuf;
use std::process::exit;
use std::sync::Arc;
use std::time::Duration;

use gpui::{
    App, AppContext as _, Bounds, Pixels, QuitMode, Size, TitlebarOptions, WindowBounds,
    WindowOptions, px, size,
};
use music::LyricsProvider;
use router::Screen;
use state::{Encore, Outcome, Toasts};
use ui::ActiveTheme as _;
use ui::ThemeKind;
use views::Root;

const LEAST_SIZE: Size<Pixels> = size(px(480.), px(400.));
const FIRST_SIZE: Size<Pixels> = size(px(920.), px(640.));
/// Some file managers launch a fresh process per selected file instead of one with every path,
/// so a multi-file "Open With" arrives here as several single-file hand-offs within milliseconds
/// of each other. This is how long to wait for the burst to go quiet before acting on it, so
/// they land as one batch instead of racing each other into the engine one at a time.
const OPEN_COALESCE: Duration = Duration::from_millis(250);

fn main() {
    memory::tune();
    let args = cli::Cli::read().opened;
    logging::init();

    let (sender, mut links) = tokio::sync::mpsc::unbounded_channel::<Vec<String>>();
    match single::claim(&args, sender.clone()) {
        single::Instance::First => {}
        single::Instance::Running => return,
        single::Instance::Failed => exit(1),
    }
    // Two rustls backends are compiled in: this crate asks for ring, while reqwest 0.13 asks for
    // aws-lc-rs. rustls refuses to guess between them, so one is picked here. A second install
    // only means another crate got there first.
    rustls::crypto::ring::default_provider()
        .install_default()
        .ok();

    let io = match state::Io::new() {
        Ok(io) => io,
        Err(error) => {
            eprintln!("encore: cannot start runtime: {error:#}");
            return;
        }
    };

    let app = gpui_platform::application()
        .with_assets(assets::Assets)
        .with_http_client(Arc::new(http::Client::new(io.handle())));
    let opened_urls_sender = sender.clone();
    app.on_open_urls(move |opened| {
        opened_urls_sender.send(opened).ok();
    });
    app.on_reopen(show_window);

    app.run(move |cx: &mut App| {
        if let Err(error) = assets::Assets.load_fonts(cx) {
            log::error!("encore: cannot load bundled fonts: {error:#}");
        }

        let database = storage::Database::standard();
        let local_provider: Arc<dyn music::MusicProvider> =
            Arc::new(music::local::LocalProvider::new(
                dirs::cache_dir()
                    .unwrap_or_else(std::env::temp_dir)
                    .join("encore"),
                database.clone(),
                storage::Cache::standard(),
            ));
        let lyrics: Vec<Arc<dyn LyricsProvider>> = vec![
            Arc::new(music::local::LocalLyrics),
            Arc::new(music::musixmatch::Musixmatch::new()),
            Arc::new(music::lrclib::LrcLib::new()),
            Arc::new(music::kugou::Kugou::new()),
            Arc::new(music::netease::NetEase::new()),
        ];
        state::init(cx, io, database, local_provider, lyrics);
        state::install_rounded_window_hook(set_toolbar_style, cx);
        let startup = Encore::global(cx).settings.read(cx).startup().to_owned();
        let start = Screen::from_id(&startup)
            .unwrap_or(Screen::Home)
            .destination();
        router::init(start, cx);
        let (look, overrides, language, pack, stillness, pace, remembered) = {
            let settings = Encore::global(cx).settings.read(cx);
            (
                settings.look(),
                settings.theme_overrides(),
                settings.language().to_owned(),
                settings.icons().to_owned(),
                settings.stillness(),
                settings.pace(),
                settings.system_theme(),
            )
        };
        i18n::set(i18n::resolve(&language));
        icons::set(&pack);
        ui::motion::apply(stillness, pace, cx);
        let reported = ThemeKind::reported(cx);
        ThemeKind::assume(reported);
        if reported != remembered {
            Encore::global(cx)
                .settings
                .clone()
                .update(cx, |settings, cx| settings.set_system_theme(reported, cx));
        }
        ui::Theme::init(look, &overrides, cx);

        let lingers = tray::install(show_window, cx);
        if lingers {
            cx.set_quit_mode(QuitMode::Explicit);
        }
        actions::register(lingers, cx);
        gestures::install(cx);
        memory::watch(cx);

        open_window(cx);

        // A cold launch with files goes through the same batch a hot hand-off uses, now that
        // state exists to open them into.
        if !args.is_empty() {
            sender.send(args.clone()).ok();
        }

        cx.spawn(async move |cx| {
            let mut pending: Vec<String> = Vec::new();
            loop {
                match links.recv().await {
                    Some(items) => pending.extend(items),
                    None => break,
                }
                // Drain whatever else arrives in the same burst before acting on any of it.
                loop {
                    cx.background_executor().timer(OPEN_COALESCE).await;
                    let mut more = false;
                    while let Ok(items) = links.try_recv() {
                        pending.extend(items);
                        more = true;
                    }
                    if !more {
                        break;
                    }
                }
                let batch = std::mem::take(&mut pending);
                cx.update(|cx| follow(&batch, cx));
            }
        })
        .detach();

        cx.activate(true);
    });
}

/// Opens what a launch or a hand-off passed as files to play. A web address is refused with a
/// toast rather than tried as a file.
fn follow(items: &[String], cx: &mut App) {
    show_window(cx);
    let mut paths: Vec<PathBuf> = Vec::new();
    for item in items {
        match is_address(item) {
            true => Toasts::show(Outcome::Failed, "toast-link-unreadable", cx),
            false => paths.extend(local_path_from_arg(item)),
        }
    }
    if !paths.is_empty() {
        let playback = Encore::global(cx).playback.clone();
        playback.update(cx, |playback, cx| playback.open_paths(paths, cx));
    }
}

/// Whether a launch argument is an address rather than a path, so it is never tried as a file.
fn is_address(item: &str) -> bool {
    !item.starts_with("file://") && item.contains("://")
}

/// A bare filesystem path, or a `file://` URI decoded back into one — the two shapes an "Open
/// With" launch or drop hands us across platforms.
fn local_path_from_arg(arg: &str) -> Option<PathBuf> {
    match arg.strip_prefix("file://") {
        Some(rest) => Some(PathBuf::from(file_uri_path(rest))),
        None => Some(PathBuf::from(arg)),
    }
}

/// A `file://` URI body turned into a filesystem path. Windows `file:///C:/…`
/// keeps a slash in front of the drive, which is not a path the OS will open.
fn file_uri_path(rest: &str) -> String {
    let decoded = percent_decode(rest);
    let path = decoded
        .strip_prefix("localhost")
        .or_else(|| decoded.strip_prefix("LOCALHOST"))
        .unwrap_or(decoded.as_str());
    path.to_owned()
}

fn percent_decode(value: &str) -> String {
    let bytes = value.as_bytes();
    let mut out = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i] == b'%'
            && i + 2 < bytes.len()
            && let Ok(hex) = std::str::from_utf8(&bytes[i + 1..i + 3])
            && let Ok(byte) = u8::from_str_radix(hex, 16)
        {
            out.push(byte);
            i += 3;
        } else {
            out.push(bytes[i]);
            i += 1;
        }
    }
    String::from_utf8_lossy(&out).into_owned()
}

fn show_window(cx: &mut App) {
    let main = cx
        .windows()
        .into_iter()
        .find(|window| !mini::is_mini(*window, cx));
    match main {
        Some(window) => {
            window
                .update(cx, |_, window, _| window.activate_window())
                .ok();
        }
        None => {
            dock::show(true);
            open_window(cx);
        }
    }
    cx.activate(true);
}

fn open_window(cx: &mut App) {
    let Encore {
        session,
        cover: _,
        equalizer: _,
        library,
        history: _,
        lyrics: _,
        network: _,
        outputs: _,
        pins: _,
        playback,
        queue,
        radio: _,
        requirements: _,
        scan: _,
        settings: _,
        signal: _,
        updates: _,
        wake: _,
    } = Encore::global(cx);
    let (session, library, playback, queue) = (
        session.clone(),
        library.clone(),
        playback.clone(),
        queue.clone(),
    );
    let (placement, display_id) = state::window_placement(LEAST_SIZE, cx)
        .map(|(placement, display_id)| (placement, Some(display_id)))
        .unwrap_or_else(|| {
            (
                WindowBounds::Windowed(Bounds::centered(None, FIRST_SIZE, cx)),
                None,
            )
        });

    let settings = Encore::global(cx).settings.read(cx);
    let saver = settings.saver();
    let traffic_lights = views::traffic_lights(settings, cx.theme().metrics.title_bar);
    let look = settings.look();
    let background = ui::backdrop(look.blur_window, look.transparent);

    cx.open_window(
        WindowOptions {
            window_bounds: Some(placement),
            display_id,
            window_background: background,
            titlebar: Some(TitlebarOptions {
                title: Some("Encore".into()),
                appears_transparent: true,
                traffic_light_position: Some(traffic_lights),
            }),
            inactive_frame_interval: saver.interval(),
            is_movable: true,
            is_resizable: true,
            app_owns_titlebar_drag: true,
            app_id: Some("encore".into()),
            window_min_size: Some(LEAST_SIZE),
            ..Default::default()
        },
        |window, cx| {
            window.set_rem_size(cx.theme().font_size);
            let handle = platform_handle(window);
            state::attach_remote(handle, cx);
            state::remember_window(window, cx);
            cx.new(|cx| Root::new(session, library, playback, queue, window, cx))
        },
    )
    .expect("failed to open window");
}

// DWM only offers two rounded presets (no arbitrary radius), so the four `Rounding` choices
// collapse onto them: `Square` turns rounding off, `Subtle` gets the small radius, and
// `Rounded`/`Round` both get the normal one, since DWM has nothing rounder than that.

/// Sizes the window's corners through the toolbar style, the one control AppKit offers over them:
/// a window without a toolbar gets the smallest radius, a compact toolbar a larger one and a
/// unified toolbar the largest. The toolbar stays empty, so only the frame changes. Before
/// macOS 26 every style shares one radius.
fn set_toolbar_style(window: &gpui::Window, rounding: ui::Rounding) {
    use objc2::{MainThreadMarker, MainThreadOnly as _};
    use objc2_app_kit::{NSToolbar, NSView, NSWindowToolbarStyle};
    use objc2_foundation::NSString;
    use raw_window_handle::{HasWindowHandle, RawWindowHandle};

    let Some(mtm) = MainThreadMarker::new() else {
        return log::warn!("window: toolbar style changed off the main thread");
    };
    let Ok(RawWindowHandle::AppKit(handle)) =
        HasWindowHandle::window_handle(window).map(|handle| handle.as_raw())
    else {
        return;
    };
    // SAFETY: GPUI hands out its live `NSView` for the window, and this runs on the main thread.
    let view: &NSView = unsafe { handle.ns_view.cast::<NSView>().as_ref() };
    let Some(native) = view.window() else {
        return;
    };

    let style = match rounding {
        ui::Rounding::Square | ui::Rounding::Subtle => None,
        ui::Rounding::Rounded => Some(NSWindowToolbarStyle::UnifiedCompact),
        ui::Rounding::Round => Some(NSWindowToolbarStyle::Unified),
    };
    match style {
        None => native.setToolbar(None),
        Some(style) => {
            if native.toolbar().is_none() {
                let toolbar = NSToolbar::initWithIdentifier(
                    NSToolbar::alloc(mtm),
                    &NSString::from_str("encore.corners"),
                );
                native.setToolbar(Some(&toolbar));
            }
            native.setToolbarStyle(style);
        }
    }
}

// DWM draws the caption buttons behind the client area, where an opaque window hides them
// and a transparent or blurred one shows them beside Encore's own. They come with
// `WS_SYSMENU`, so that is the style to drop: `WS_CAPTION` has to stay, because DWM only
// animates minimize, restore and close on a window that carries it. Alt+F4 and the taskbar
// still close the window; only the Alt+Space menu goes, and Encore's title bar has no use
// for it.

fn platform_handle(_window: &gpui::Window) -> Option<*mut std::ffi::c_void> {
    None
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_percent_before_a_multibyte_letter_stays_in_the_path() {
        assert_eq!(percent_decode("%가나"), "%가나");
        assert_eq!(percent_decode("%a가"), "%a가");
        assert_eq!(percent_decode("a%20b%EA%B0%80"), "a b가");
    }
}
