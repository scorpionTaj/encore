use gpui::prelude::*;
use gpui::{AnyElement, App, Context, SharedString, Window};
use i18n::{Language, t};
use router::{NavEntry, Screen};
use ui::{MenuItem, OptionCard, Pace, Picker, Rounding, Saver, Stillness, Switch};

use super::appearance::corner;
use super::rows::{grid, segmented};
use super::{Group, LANGUAGES, SettingsView, Slot};
use crate::shared::popups::matches_query;

const STARTUP: &str = "startup";
const ENTRIES: &str = "entries";

impl SettingsView {
    pub(super) fn general_groups(&self, _cx: &App) -> Vec<Group> {
        vec![
            Group::new("settings-group-startup", "icons/house.svg").rows([
                Slot::Startup,
                Slot::Entries,
                Slot::Language,
            ]),
            Group::new("settings-group-window", "icons/panel-left-open.svg")
                .rows([Slot::Tray, Slot::TrayIcon])
                .rows([Slot::Decorations, Slot::WindowRounding]),
            Group::new("settings-group-motion", "icons/loader-circle.svg").rows([
                Slot::Motion,
                Slot::Pace,
                Slot::Saver,
            ]),
            Group::new("settings-group-context-menus", "icons/list.svg").rows([Slot::AdaptiveMenu]),
        ]
    }

    pub(super) fn startup_row(&self, cx: &mut Context<Self>) -> AnyElement {
        let chosen = Screen::from_id(self.settings.read(cx).startup()).unwrap_or(Screen::Home);
        let current = i18n::lookup(chosen.key(), None);

        let picker = Picker::new(STARTUP, &self.popovers, current)
            .width(Picker::NARROW)
            .items(Screen::ALL.map(|screen| {
                let item = MenuItem::new(screen.id(), i18n::lookup(screen.key(), None))
                    .selected(screen == chosen);
                item.on_click(cx.listener(move |this, _, _, cx| {
                    this.settings
                        .update(cx, |settings, cx| settings.set_startup(screen.id(), cx));
                    cx.notify();
                }))
            }));

        self.row(t!("settings-startup"), picker.into_any_element())
    }

    pub(super) fn entries_row(&self, cx: &mut Context<Self>) -> AnyElement {
        let picker = Picker::new(ENTRIES, &self.popovers, t!("settings-entries-pick"))
            .width(Picker::REGULAR)
            .sticky()
            .items(NavEntry::ALL.map(|entry| {
                let shown = self.settings.read(cx).nav_shown(entry.id());

                MenuItem::new(entry.id(), i18n::lookup(entry.key(), None))
                    .selected(shown)
                    .on_click(cx.listener(move |this, _, _, cx| {
                        this.settings.update(cx, |settings, cx| {
                            settings.set_nav_shown(entry.id(), !shown, cx)
                        });
                        cx.notify();
                    }))
            }));

        self.row(t!("settings-entries"), picker.into_any_element())
    }

    pub(super) fn language_row(&self, cx: &mut Context<Self>) -> AnyElement {
        let chosen = self.settings.read(cx).language().to_owned();
        let current = match Language::from_id(&chosen) {
            Some(language) => SharedString::from(language.label()),
            None => t!("settings-language-system"),
        };

        let asked = self.languages.query();
        let entries = std::iter::once((i18n::AUTO, t!("settings-language-system")))
            .chain(
                Language::ALL
                    .into_iter()
                    .map(|language| (language.id(), SharedString::from(language.label()))),
            )
            .filter(|(id, label)| matches_query(id, label, &asked))
            .collect::<Vec<_>>();
        let barren = entries.is_empty();
        let count = entries.len();
        let cursor = self.languages.cursor(count);
        let submitted = entries.clone();

        let picker = Picker::new(LANGUAGES, &self.popovers, current)
            .width(Picker::WIDE)
            .menu(self.languages.menu("languages-menu", Picker::WIDE))
            .items(entries.into_iter().enumerate().map(|(place, (id, label))| {
                MenuItem::new(id, label)
                    .selected(place == cursor)
                    .checked(chosen == id)
                    .on_click(cx.listener(move |this, _, _, cx| {
                        this.settings
                            .update(cx, |settings, cx| settings.set_language(id, cx));
                        this.popovers.close();
                        cx.notify();
                    }))
            }))
            .when(barren, |picker| {
                picker
                    .item(MenuItem::new("language-empty", t!("settings-language-none")).disabled())
            });
        let picker = self.languages.controls(
            picker,
            count,
            move |this, place, _, cx| {
                let Some((id, _)) = submitted.get(place) else {
                    return;
                };
                this.settings
                    .update(cx, |settings, cx| settings.set_language(*id, cx));
                this.popovers.close();
                cx.notify();
            },
            cx,
        );

        self.row(t!("settings-language"), picker.into_any_element())
    }

    pub(super) fn decorations_row(&self, cx: &mut Context<Self>) -> AnyElement {
        let on = self.settings.read(cx).window_controls();

        self.row(
            t!("settings-window-controls"),
            Switch::new("window-controls", on)
                .on_click(cx.listener(move |this, _, _, cx| {
                    this.settings
                        .update(cx, |settings, cx| settings.set_window_controls(!on, cx));
                }))
                .into_any_element(),
        )
    }

    /// The window's corners as one card per rounding, each showing a corner at that radius.
    /// AppKit has no square window, so macOS offers only the corners it can draw.
    pub(super) fn window_rounding_row(
        &self,
        window: &Window,
        cx: &mut Context<Self>,
    ) -> AnyElement {
        let current = self.settings.read(cx).window_rounding();
        let offered = Rounding::ALL
            .into_iter()
            .filter(|rounding| *rounding != Rounding::Square)
            .collect::<Vec<_>>();

        let cards =
            grid(offered.len() as u16, window, cx).children(offered.into_iter().map(|rounding| {
                OptionCard::new(SharedString::from(format!("window-{}", rounding.id())))
                    .thumb(corner(rounding, cx))
                    .label(rounding.label())
                    .selected(current == rounding)
                    .on_click(cx.listener(move |this, _, _, cx| {
                        this.settings.update(cx, |settings, cx| {
                            settings.set_window_rounding(rounding, cx)
                        });
                        cx.notify();
                    }))
            }));

        self.stacked(t!("settings-window-rounding"), cards)
    }

    pub(super) fn motion_row(&self, cx: &mut Context<Self>) -> AnyElement {
        let current = self.settings.read(cx).stillness();

        let choices = segmented(
            "motion",
            Stillness::ALL.map(|choice| (choice, choice.label())),
            current,
            cx.listener(|this, choice: &Stillness, _, cx| {
                let choice = *choice;
                this.settings
                    .update(cx, |settings, cx| settings.set_stillness(choice, cx));
                cx.notify();
            }),
        );

        self.row(t!("settings-motion"), choices.into_any_element())
    }

    pub(super) fn pace_row(&self, cx: &mut Context<Self>) -> AnyElement {
        let current = self.settings.read(cx).pace();

        let choices = segmented(
            "pace",
            Pace::ALL.map(|choice| (choice, choice.label())),
            current,
            cx.listener(|this, choice: &Pace, _, cx| {
                let choice = *choice;
                this.settings
                    .update(cx, |settings, cx| settings.set_pace(choice, cx));
                cx.notify();
            }),
        );

        self.row(t!("settings-pace"), choices.into_any_element())
    }

    pub(super) fn saver_row(&self, cx: &mut Context<Self>) -> AnyElement {
        let current = self.settings.read(cx).saver();

        let choices = segmented(
            "saver",
            Saver::ALL.map(|choice| (choice, choice.label())),
            current,
            cx.listener(|this, choice: &Saver, _, cx| {
                let choice = *choice;
                this.settings
                    .update(cx, |settings, cx| settings.set_saver(choice, cx));
                cx.notify();
            }),
        );

        super::Row::new(t!("settings-saver"), choices.into_any_element())
            .detail(t!("settings-saver-detail"))
            .into_any_element()
    }

    pub(super) fn adaptive_menu_row(&self, cx: &mut Context<Self>) -> AnyElement {
        let on = self.settings.read(cx).adaptive_menu();

        self.row(
            t!("settings-adaptive-menu"),
            Switch::new("adaptive-menu", on)
                .on_click(cx.listener(move |this, _, _, cx| {
                    this.settings
                        .update(cx, |settings, cx| settings.set_adaptive_menu(!on, cx));
                }))
                .into_any_element(),
        )
    }

    pub(super) fn tray_row(&self, cx: &mut Context<Self>) -> AnyElement {
        let on = self.settings.read(cx).close_to_tray();

        self.row(
            t!("settings-close-to-tray"),
            Switch::new("close-to-tray", on)
                .on_click(cx.listener(move |this, _, _, cx| {
                    this.settings
                        .update(cx, |settings, cx| settings.set_close_to_tray(!on, cx));
                }))
                .into_any_element(),
        )
    }

    pub(super) fn tray_icon_row(&self, cx: &mut Context<Self>) -> AnyElement {
        let on = self.settings.read(cx).tray_icon();

        self.row(
            t!("settings-tray-icon"),
            Switch::new("tray-icon", on)
                .on_click(cx.listener(move |this, _, _, cx| {
                    this.settings
                        .update(cx, |settings, cx| settings.set_tray_icon(!on, cx));
                }))
                .into_any_element(),
        )
    }
}
