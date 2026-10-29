use std::path::PathBuf;

use gpui::prelude::*;
use gpui::{AnyElement, Context, SharedString, div};
use i18n::t;
use state::{Encore, Scan};
use ui::{ActiveTheme as _, Button, Switch, Text};

use super::{Group, SettingsView, Slot};
use crate::shared::{local, text};

impl SettingsView {
    pub(super) fn music_folders_groups(&self) -> Vec<Group> {
        vec![Group::untitled().rows([Slot::Folders, Slot::WatchFolders])]
    }

    /// The switch that has Encore follow the music folders and pick up songs added or removed
    /// there by itself.
    pub(super) fn watch_folders_row(&self, cx: &mut Context<Self>) -> AnyElement {
        let on = self.settings.read(cx).watch_folders();

        self.row(
            t!("settings-watch-folders"),
            Switch::new("watch-folders", on)
                .on_click(cx.listener(move |this, _, _, cx| {
                    this.settings
                        .update(cx, |settings, cx| settings.set_watch_folders(!on, cx));
                }))
                .into_any_element(),
        )
    }

    /// The music folders page's list: one row per watched folder with its remove button, then
    /// the row that adds a folder or scans them all again, with how the last scan went.
    pub(super) fn folders(&self, cx: &mut Context<Self>) -> AnyElement {
        let theme = *cx.theme();
        let paths = self.session.read(cx).local_paths();

        let add = local::choose_button("add-local-folder")
            .label(t!("settings-add-folder"))
            .icon("icons/folder-plus.svg")
            .small()
            .primary();

        let scan = Scan::global(cx).read(cx);
        let scanning = scan.progress();
        // Until the walk is over there is no total to be a fraction of, and on a network share
        // that is the longest part, so it says so rather than showing nothing.
        let note = match (scanning, scan.done()) {
            (Some(progress), _) => Some(match progress.percent() {
                Some(percent) => t!("settings-scan-progress", percent = percent),
                None => t!("settings-scan-walking"),
            }),
            (None, Some(took)) => Some(t!("settings-scan-done", seconds = text::lapsed(took))),
            (None, None) => None,
        };

        let rescan = (!paths.is_empty()).then(|| {
            Button::new("rescan-local-folder")
                .label(t!("settings-rescan"))
                .icon("icons/refresh-cw.svg")
                .small()
                .outline()
                .disabled(scanning.is_some())
                .on_click(cx.listener(|this, _, _, cx| this.rescan_local_folder(cx)))
        });

        let items = paths
            .into_iter()
            .enumerate()
            .map(|(index, path)| Self::local_folder_item(index, path, cx))
            .collect::<Vec<_>>();
        let empty = items.is_empty().then(|| {
            div()
                .flex()
                .items_center()
                .h(theme.metrics.list_row)
                .text_color(theme.muted_foreground)
                .child(t!("settings-local-folder-none"))
        });

        div()
            .flex()
            .flex_col()
            .children(items)
            .children(empty)
            .child(
                div()
                    .flex()
                    .items_center()
                    .gap_2()
                    .h(theme.metrics.list_row)
                    .child(add)
                    .children(rescan)
                    .child(div().flex_1())
                    .children(note.map(|note| {
                        div()
                            .flex_none()
                            .text_color(theme.muted_foreground)
                            .text_size(theme.text(Text::Label))
                            .child(note)
                    })),
            )
            .into_any_element()
    }

    /// One watched folder: its path and the button that stops watching it.
    fn local_folder_item(index: usize, path: String, cx: &mut Context<Self>) -> AnyElement {
        let theme = *cx.theme();

        div()
            .id(("local-folder-item", index))
            .flex()
            .items_center()
            .justify_between()
            .gap_3()
            .h(theme.metrics.list_row)
            .child(
                div()
                    .flex_1()
                    .min_w_0()
                    .truncate()
                    .child(SharedString::from(path.clone())),
            )
            .child(
                Button::new(("remove-local-folder", index))
                    .ghost()
                    .icon("icons/trash-2.svg")
                    .size_8()
                    .px_0()
                    .tooltip("settings-remove-folder")
                    .tint(theme.muted_foreground)
                    .on_click(cx.listener(move |this, _, _, cx| {
                        this.remove_local_folder(path.clone(), cx)
                    })),
            )
            .into_any_element()
    }

    pub(super) fn rescan_local_folder(&mut self, cx: &mut Context<Self>) {
        Scan::global(cx).update(cx, |scan, _| scan.asked());
        Encore::global(cx)
            .library
            .clone()
            .update(cx, |library, cx| library.rescan_local(true, cx));
    }

    pub(super) fn remove_local_folder(&mut self, path: String, cx: &mut Context<Self>) {
        Encore::global(cx)
            .library
            .clone()
            .update(cx, |library, cx| {
                library.remove_local_folder(PathBuf::from(path), cx)
            });
    }
}
