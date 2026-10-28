use std::time::Duration;

use gpui::App;
use state::Encore;

/// How often the footprint is logged under debug logging.
const INTERVAL: Duration = Duration::from_secs(10);

pub fn tune() {}

/// Logs the memory footprint every `INTERVAL` while debug logging is on, and does nothing
/// otherwise.
pub fn watch(cx: &mut App) {
    if !log::log_enabled!(log::Level::Debug) {
        return;
    }
    cx.spawn(async move |cx| {
        loop {
            cx.background_executor().timer(INTERVAL).await;
            let probed = cx
                .background_executor()
                .spawn(async { (footprint().unwrap_or_default(), resident()) })
                .await;
            cx.update(|cx| report(probed, cx));
        }
    })
    .detach();
}

fn report(probed: (Footprint, Option<usize>), cx: &mut App) {
    let (footprint, resident) = probed;
    let (entries, bytes) = ui::artwork_usage(cx).unwrap_or((0, 0));
    let queue = Encore::global(cx).queue.read(cx);
    let past = queue.past().len();
    let ahead = queue.upcoming().len() + queue.similar().len();

    log::debug!(
        "memory: rss {}, heap {}, gpu {}, file {}, artwork {entries} entries / {}, queue {past} past / {ahead} ahead",
        resident.map_or_else(|| "unknown".to_owned(), mib),
        mib(footprint.heap),
        mib(footprint.gpu),
        mib(footprint.file),
        mib(bytes)
    );
}

#[derive(Default)]
struct Footprint {
    heap: usize,
    gpu: usize,
    file: usize,
}

fn footprint() -> Option<Footprint> {
    None
}

fn mib(bytes: usize) -> String {
    format!("{:.1} MiB", bytes as f64 / (1024. * 1024.))
}

fn resident() -> Option<usize> {
    None
}
