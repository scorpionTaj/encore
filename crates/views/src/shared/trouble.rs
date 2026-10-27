use gpui::{App, ClickEvent, ElementId, SharedString, Window};
use i18n::t;
use state::Network;
use ui::{Button, Vacancy};

/// The page-filling state for a screen that cannot load, with a button that tries again.
/// `reason` is what a load came back with, or `None` when the app already knows the network is
/// gone and the screen never tried. A reason that reads as a lost connection becomes the same
/// No connection state; anything else keeps its own text under `label`, which is why every
/// caller passes the caption for its own page rather than one shared line. In offline mode
/// every screen says so instead, with a button that goes online.
pub(crate) fn lost(
    id: impl Into<ElementId>,
    label: SharedString,
    reason: Option<&str>,
    retry: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    cx: &App,
) -> Vacancy {
    if Network::offline(cx) {
        return offline();
    }
    let vacancy = match reason.filter(|reason| !music::trouble::offline(reason)) {
        None => Vacancy::new(t!("trouble-offline"))
            .detail(t!("trouble-offline-detail"))
            .icon("icons/wifi-off.svg"),
        Some(reason) => Vacancy::new(label)
            .detail(SharedString::from(reason.to_owned()))
            .icon("icons/circle-alert.svg"),
    };

    vacancy.action(
        Button::new(id)
            .label(t!("trouble-retry"))
            .icon("icons/refresh-cw.svg")
            .outline()
            .on_click(retry),
    )
}

/// The page-filling state for offline mode, with a button that turns it off.
pub(crate) fn offline() -> Vacancy {
    Vacancy::new(t!("offline-title"))
        .icon("icons/wifi-off.svg")
        .action(
            Button::new("go-online")
                .label(t!("offline-go-online"))
                .outline()
                .on_click(|_, _, cx| {
                    let network = Network::global(cx);
                    network.update(cx, |network, cx| network.set_offline(false, cx));
                }),
        )
}
