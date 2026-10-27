use gpui::{App, Context, Entity, EventEmitter};

use crate::{AppSettings, Encore};

/// Raised when the network came back, so a screen that gave up can load again.
pub struct Reconnected;

/// Whether the app can reach the network at all, as one answer for the whole app rather than
/// one per screen. It goes offline on the first failure that reads as a lost connection and
/// comes back when something reaches the network again. Offline mode reads as a network that is
/// gone for as long as the listener keeps it on.
pub struct Network {
    settings: Entity<AppSettings>,
    lost: bool,
    /// Whether the listener turned offline mode on.
    offline: bool,
}

impl EventEmitter<Reconnected> for Network {}

impl Network {
    pub fn new(settings: Entity<AppSettings>, cx: &App) -> Self {
        let offline = settings.read(cx).offline_mode();
        Self {
            settings,
            lost: false,
            offline,
        }
    }

    pub fn global(cx: &App) -> Entity<Self> {
        Encore::global(cx).network.clone()
    }

    /// Whether the network is gone, or offline mode is on. Every screen that needs one asks this
    /// rather than waiting for its own load to fail.
    pub fn lost(cx: &App) -> bool {
        match cx.try_global::<Encore>() {
            Some(encore) => {
                let network = encore.network.read(cx);
                network.lost || network.offline
            }
            None => false,
        }
    }

    /// Whether the listener turned offline mode on, which a screen tells apart from a lost
    /// connection by offering to go online rather than to try again.
    pub fn offline(cx: &App) -> bool {
        cx.try_global::<Encore>()
            .is_some_and(|encore| encore.network.read(cx).offline)
    }

    /// Turns offline mode on or off and remembers it for the next launch. Going online with the
    /// network there raises [`Reconnected`], so every screen loads again.
    pub fn set_offline(&mut self, offline: bool, cx: &mut Context<Self>) {
        if self.offline == offline {
            return;
        }
        log::info!(
            "network: offline mode is {}",
            match offline {
                true => "on",
                false => "off",
            }
        );
        self.offline = offline;
        self.settings
            .update(cx, |settings, cx| settings.set_offline_mode(offline, cx));
        cx.notify();
        cx.refresh_windows();
        if !offline && !self.lost {
            cx.emit(Reconnected);
        }
    }

    /// Notes a failed call. Only a reason that reads as a lost connection puts the app offline;
    /// anything the provider refused leaves it alone.
    pub fn failed(reason: &str, cx: &mut App) {
        if !music::trouble::offline(reason) {
            return;
        }
        let Some(network) = cx
            .try_global::<Encore>()
            .map(|encore| encore.network.clone())
        else {
            return;
        };
        network.update(cx, |this, cx| {
            if this.lost {
                return;
            }
            log::warn!("network: the connection is gone: {reason}");
            this.lost = true;
            cx.notify();
            cx.refresh_windows();
        });
    }

    /// Notes that something reached the network, which is the quickest way back online.
    pub fn reached(cx: &mut App) {
        let Some(network) = cx
            .try_global::<Encore>()
            .map(|encore| encore.network.clone())
        else {
            return;
        };
        network.update(cx, |this, cx| this.found(cx));
    }

    fn found(&mut self, cx: &mut Context<Self>) {
        if !self.lost {
            return;
        }
        log::info!("network: the connection is back");
        self.lost = false;
        cx.notify();
        cx.refresh_windows();
        // In offline mode nothing is let back on the network until the listener goes online.
        if !self.offline {
            cx.emit(Reconnected);
        }
    }
}
