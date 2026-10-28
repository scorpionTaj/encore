//! Links handed to the app from outside, by the system or on the command line. Encore's own
//! `encore:` links, and Sonora's `sonora:` ones, offer a theme to install.

use gpui::{Context, Entity};

use crate::settings::{is_own_link, read_theme_link};
use crate::{AppSettings, Outcome, ThemeOffer, Toasts};

/// Reads theme links. A theme waits in `theme_offer` until the user installs it or turns it
/// down.
pub struct Links {
    settings: Entity<AppSettings>,
    theme: Option<ThemeOffer>,
}

impl Links {
    pub fn new(settings: Entity<AppSettings>, _cx: &mut Context<Self>) -> Self {
        Self {
            settings,
            theme: None,
        }
    }

    /// Takes `text` when it is a theme link and says whether it was one. A link in Encore's or
    /// Sonora's scheme that does not read as a theme is refused with a toast.
    pub fn open(&mut self, text: &str, cx: &mut Context<Self>) -> bool {
        if !is_own_link(text) {
            return false;
        }
        match read_theme_link(text) {
            Ok(offer) => {
                self.theme = Some(offer);
                cx.notify();
            }
            Err(error) => {
                log::warn!("links: cannot read a theme link: {error:#}");
                Toasts::show(Outcome::Failed, "toast-theme-link-unreadable", cx);
            }
        }
        true
    }

    /// The theme a link offered, while the user has not answered it.
    pub fn theme_offer(&self) -> Option<&ThemeOffer> {
        self.theme.as_ref()
    }

    pub fn decline_theme(&mut self, cx: &mut Context<Self>) {
        if self.theme.take().is_some() {
            cx.notify();
        }
    }

    /// Installs and selects the offered theme and says whether it worked. A failure is logged
    /// and shown as a toast.
    pub fn install_theme(&mut self, cx: &mut Context<Self>) -> bool {
        let Some(offer) = self.theme.take() else {
            return false;
        };
        cx.notify();
        let installed = self
            .settings
            .update(cx, |settings, cx| settings.install_theme(&offer, cx));
        match installed {
            Ok(id) => {
                log::info!("links: installed theme {id}");
                true
            }
            Err(error) => {
                log::warn!("links: cannot install theme: {error:#}");
                Toasts::about(
                    Outcome::Failed,
                    "toast-theme-install-failed",
                    offer.variant.title,
                    cx,
                );
                false
            }
        }
    }
}
