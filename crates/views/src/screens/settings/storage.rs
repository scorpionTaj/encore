use gpui::prelude::*;
use gpui::{AnyElement, App, Context};
use i18n::t;
use state::Network;
use ui::Switch;

use super::{Group, SettingsView, Slot};

impl SettingsView {
    /// The storage section: offline mode, which keeps lyrics off the network.
    pub(super) fn storage_groups(&self, _cx: &App) -> Vec<Group> {
        vec![Group::untitled().rows([Slot::OfflineMode])]
    }

    /// Offline mode, which keeps the app off the network until it is turned off again.
    pub(super) fn offline_mode_row(&self, cx: &mut Context<Self>) -> AnyElement {
        let on = Network::offline(cx);
        self.row(
            t!("settings-offline-mode"),
            Switch::new("offline-mode", on)
                .on_click(move |_, _, cx| {
                    let network = Network::global(cx);
                    network.update(cx, |network, cx| network.set_offline(!on, cx));
                })
                .into_any_element(),
        )
    }
}
