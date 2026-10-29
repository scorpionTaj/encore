use std::time::Duration;

use gpui::accesskit::Role;
use gpui::prelude::*;
use gpui::{App, Context, Entity, FocusHandle, Global, Render, SharedString, Window, div};
use i18n::t;
use music::Track;
use state::lyrics_file::{self, LyricLine};
use state::{Encore, Outcome, Toasts};
use ui::{ActiveTheme as _, Button, Dismiss, FORM_CONTEXT, Input, Modal, Scrollbar, Text};

/// One line being edited: when it starts, and its text. `id` stays with the line while lines
/// are added and removed around it.
struct Row {
    id: usize,
    at: Option<Duration>,
    input: Entity<Input>,
}

/// The lyrics editor for one local song, a line at a time. Each line takes its start from the
/// playing position with Stamp, and Save writes the lines to an `.lrc` file beside the song.
pub(crate) struct LyricsEditor {
    track: Option<Track>,
    rows: Vec<Row>,
    next_id: usize,
    scrollbar: Entity<Scrollbar>,
    focus: FocusHandle,
    restore: Option<FocusHandle>,
}

struct Installed(Entity<LyricsEditor>);

impl Global for Installed {}

impl LyricsEditor {
    pub fn entity(cx: &mut App) -> Entity<Self> {
        if cx.try_global::<Installed>().is_none() {
            let editor = cx.new(|cx| {
                let id = cx.entity_id();
                Self {
                    track: None,
                    rows: Vec::new(),
                    next_id: 0,
                    scrollbar: cx.new(|_| Scrollbar::inset().watching(id)),
                    focus: cx.focus_handle(),
                    restore: None,
                }
            });
            cx.set_global(Installed(editor));
        }
        cx.global::<Installed>().0.clone()
    }

    pub fn open(track: Track, window: &mut Window, cx: &mut App) {
        let editor = Self::entity(cx);
        editor.update(cx, |this, cx| this.show(track, window, cx));
    }

    fn show(&mut self, track: Track, window: &mut Window, cx: &mut Context<Self>) {
        self.restore = window.focused(cx);
        let mut lines = lyrics_file::read_lrc(&track);
        if lines.is_empty() {
            lines.push(LyricLine::default());
        }
        self.rows = Vec::new();
        for line in lines {
            self.push(line, None, cx);
        }
        self.track = Some(track);
        window.focus(&self.focus, cx);
        cx.notify();
    }

    fn close(&mut self, window: &mut Window, cx: &mut Context<Self>) {
        self.track = None;
        self.rows.clear();
        if let Some(focus) = self.restore.take() {
            window.focus(&focus, cx);
        }
        cx.notify();
    }

    /// Adds `line` after the row at `after`, or at the end for `None`.
    fn push(&mut self, line: LyricLine, after: Option<usize>, cx: &mut Context<Self>) {
        let input = cx.new(|cx| Input::new("lyrics-line-hint", cx));
        input.update(cx, |input, cx| input.set_text(line.text, cx));
        let row = Row {
            id: self.next_id,
            at: line.at,
            input,
        };
        self.next_id += 1;
        match after {
            Some(index) => self.rows.insert(index + 1, row),
            None => self.rows.push(row),
        }
    }

    /// Sets the line's start to where the playing song is now.
    fn stamp(&mut self, index: usize, cx: &mut Context<Self>) {
        let playback = Encore::global(cx).playback.clone();
        let (elapsed, _) = playback.read(cx).shown_time(cx);
        if let Some(row) = self.rows.get_mut(index) {
            row.at = Some(elapsed);
        }
        cx.notify();
    }

    fn remove(&mut self, index: usize, cx: &mut Context<Self>) {
        if self.rows.len() > 1 && index < self.rows.len() {
            self.rows.remove(index);
        }
        cx.notify();
    }

    /// Writes the lines to the `.lrc` file, timed ones in time order, and shows them.
    fn save(&mut self, window: &mut Window, cx: &mut Context<Self>) {
        let Some(track) = self.track.clone() else {
            return;
        };
        let mut lines: Vec<LyricLine> = self
            .rows
            .iter()
            .map(|row| LyricLine {
                at: row.at,
                text: row.input.read(cx).text().trim().to_owned(),
            })
            .filter(|line| line.at.is_some() || !line.text.is_empty())
            .collect();
        if lines.iter().all(|line| line.at.is_some()) {
            lines.sort_by_key(|line| line.at);
        }
        match lyrics_file::write_lrc(&track, &lines) {
            Ok(_) => {
                Toasts::about(Outcome::Done, "toast-lyrics-saved", track.name.clone(), cx);
                let lyrics = Encore::global(cx).lyrics.clone();
                lyrics.update(cx, |lyrics, cx| lyrics.reread(cx));
                self.close(window, cx);
            }
            Err(error) => {
                log::warn!("lyrics: cannot save {}: {error:#}", track.name);
                Toasts::show(Outcome::Failed, "toast-lyrics-save-failed", cx);
            }
        }
    }

    fn line(&self, index: usize, cx: &mut Context<Self>) -> impl IntoElement + use<> {
        let theme = cx.theme();
        let row = &self.rows[index];
        let stamp = row
            .at
            .map(|at| SharedString::from(lyrics_file::stamp(at)))
            .unwrap_or_else(|| SharedString::new_static("[--:--.--]"));
        div()
            .id(("lyric-line", row.id))
            .role(Role::ListItem)
            .aria_label(t!("lyrics-line-label", number = index + 1))
            .aria_position_in_set(index + 1)
            .aria_size_of_set(self.rows.len())
            .flex()
            .items_center()
            .gap_2()
            .child(
                Button::new(("lyric-stamp", row.id))
                    .small()
                    .ghost()
                    .label(stamp)
                    .tooltip("lyrics-stamp")
                    .on_click(cx.listener(move |this, _, _, cx| this.stamp(index, cx))),
            )
            .child(div().flex_1().min_w_0().child(row.input.clone()))
            .child(
                Button::new(("lyric-add", row.id))
                    .small()
                    .ghost()
                    .icon("icons/plus.svg")
                    .tooltip("lyrics-add-line")
                    .on_click(cx.listener(move |this, _, _, cx| {
                        this.push(LyricLine::default(), Some(index), cx);
                        cx.notify();
                    })),
            )
            .child(
                Button::new(("lyric-remove", row.id))
                    .small()
                    .ghost()
                    .icon("icons/x.svg")
                    .tooltip("lyrics-remove-line")
                    .disabled(self.rows.len() <= 1)
                    .on_click(cx.listener(move |this, _, _, cx| this.remove(index, cx))),
            )
            .text_size(theme.text(Text::Small))
    }
}

impl Render for LyricsEditor {
    fn render(&mut self, window: &mut Window, cx: &mut Context<Self>) -> impl IntoElement {
        let Some(track) = self.track.clone() else {
            if let Some(focus) = self.restore.take() {
                window.focus(&focus, cx);
            }
            return div().into_any_element();
        };
        let theme = *cx.theme();
        let lines: Vec<_> = (0..self.rows.len())
            .map(|index| self.line(index, cx).into_any_element())
            .collect();

        div()
            .absolute()
            .inset_0()
            .key_context(FORM_CONTEXT)
            .track_focus(&self.focus)
            .on_action(cx.listener(|this, _: &Dismiss, window, cx| {
                cx.stop_propagation();
                this.close(window, cx);
            }))
            .child(
                Modal::new("lyrics-editor", t!("lyrics-edit-title"))
                    .w(theme.metrics.cover * 3.6)
                    .h(theme.metrics.cover * 4.)
                    .detail(SharedString::from(track.name.clone()))
                    .child(
                        div()
                            .text_size(theme.text(Text::Small))
                            .text_color(theme.muted_foreground)
                            .child(t!("lyrics-edit-help")),
                    )
                    .child(
                        ui::Scroller::new("lyrics-editor-lines", &self.scrollbar)
                            .flex_1()
                            .child(
                                div()
                                    .id("lyrics-editor-list")
                                    .role(Role::List)
                                    .aria_label(t!("lyrics-title"))
                                    .flex()
                                    .flex_col()
                                    .gap_1()
                                    .children(lines),
                            ),
                    )
                    .action(
                        Button::new("cancel-lyrics-edit")
                            .ghost()
                            .label(t!("common-cancel"))
                            .on_click(cx.listener(|this, _, window, cx| this.close(window, cx))),
                    )
                    .action(
                        Button::new("save-lyrics-edit")
                            .primary()
                            .label(t!("common-save"))
                            .on_click(cx.listener(|this, _, window, cx| this.save(window, cx))),
                    )
                    .on_dismiss(cx.listener(|this, _, window, cx| this.close(window, cx))),
            )
            .into_any_element()
    }
}
