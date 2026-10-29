use gpui::accesskit::Role;
use gpui::prelude::*;
use gpui::{App, Context, Entity, FocusHandle, Global, Render, Window, div};
use i18n::t;
use state::{Encore, Rule, RuleField, SmartPlaylist};
use ui::{ActiveTheme as _, Button, Dismiss, FORM_CONTEXT, Input, Modal, Scrollbar, Text};

/// One rule being edited, with the input that holds its value. `id` stays with the rule while
/// rules are added and removed around it.
struct RuleRow {
    id: usize,
    rule: Rule,
    value: Entity<Input>,
}

/// Creates or changes one smart playlist: its name, whether songs pass every rule or any, the
/// rules, and the most songs it holds.
pub(crate) struct SmartEditor {
    editing: Option<SmartPlaylist>,
    /// Whether the playlist being edited is already saved, which offers Delete.
    saved: bool,
    name: Entity<Input>,
    limit: Entity<Input>,
    rows: Vec<RuleRow>,
    next_id: usize,
    scrollbar: Entity<Scrollbar>,
    focus: FocusHandle,
    restore: Option<FocusHandle>,
}

struct Installed(Entity<SmartEditor>);

impl Global for Installed {}

impl SmartEditor {
    pub fn entity(cx: &mut App) -> Entity<Self> {
        if cx.try_global::<Installed>().is_none() {
            let editor = cx.new(|cx| {
                let id = cx.entity_id();
                Self {
                    editing: None,
                    saved: false,
                    name: cx.new(|cx| Input::new("smart-name-hint", cx)),
                    limit: cx.new(|cx| Input::new("smart-limit-hint", cx)),
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

    /// Opens the editor on `playlist`, or on a new one for `None`.
    pub fn open(playlist: Option<SmartPlaylist>, window: &mut Window, cx: &mut App) {
        let editor = Self::entity(cx);
        editor.update(cx, |this, cx| this.show(playlist, window, cx));
    }

    fn show(
        &mut self,
        playlist: Option<SmartPlaylist>,
        window: &mut Window,
        cx: &mut Context<Self>,
    ) {
        self.restore = window.focused(cx);
        self.saved = playlist.is_some();
        let playlist =
            playlist.unwrap_or_else(|| SmartPlaylist::new(t!("smart-new-name").to_string()));
        let name = playlist.name.clone();
        let limit = playlist
            .limit
            .map(|limit| limit.to_string())
            .unwrap_or_default();
        self.name.update(cx, |input, cx| input.set_text(name, cx));
        self.limit.update(cx, |input, cx| input.set_text(limit, cx));
        self.rows = Vec::new();
        for rule in playlist.rules.clone() {
            self.push(rule, cx);
        }
        self.editing = Some(playlist);
        window.focus(&self.focus, cx);
        cx.notify();
    }

    fn close(&mut self, window: &mut Window, cx: &mut Context<Self>) {
        self.editing = None;
        self.rows.clear();
        if let Some(focus) = self.restore.take() {
            window.focus(&focus, cx);
        }
        cx.notify();
    }

    fn push(&mut self, rule: Rule, cx: &mut Context<Self>) {
        let value = cx.new(|cx| Input::new("smart-value-hint", cx));
        let text = rule.value.clone();
        value.update(cx, |input, cx| input.set_text(text, cx));
        self.rows.push(RuleRow {
            id: self.next_id,
            rule,
            value,
        });
        self.next_id += 1;
    }

    /// Moves the rule at `index` to the next field, with that field's first comparison.
    fn next_field(&mut self, index: usize, cx: &mut Context<Self>) {
        if let Some(row) = self.rows.get_mut(index) {
            let fields = RuleField::ALL;
            let at = fields
                .iter()
                .position(|field| *field == row.rule.field)
                .unwrap_or(0);
            let field = fields[(at + 1) % fields.len()];
            row.rule.field = field;
            row.rule.op = field.ops()[0];
        }
        cx.notify();
    }

    /// Moves the rule at `index` to its field's next comparison.
    fn next_op(&mut self, index: usize, cx: &mut Context<Self>) {
        if let Some(row) = self.rows.get_mut(index) {
            let ops = row.rule.field.ops();
            let at = ops.iter().position(|op| *op == row.rule.op).unwrap_or(0);
            row.rule.op = ops[(at + 1) % ops.len()];
        }
        cx.notify();
    }

    fn save(&mut self, window: &mut Window, cx: &mut Context<Self>) {
        let Some(mut playlist) = self.editing.clone() else {
            return;
        };
        let name = self.name.read(cx).text().trim().to_owned();
        if !name.is_empty() {
            playlist.name = name;
        }
        playlist.limit = self.limit.read(cx).text().trim().parse().ok();
        playlist.rules = self
            .rows
            .iter()
            .map(|row| Rule {
                value: row.value.read(cx).text().trim().to_owned(),
                ..row.rule.clone()
            })
            .collect();
        let smart = Encore::global(cx).smart.clone();
        smart.update(cx, |smart, cx| smart.save(playlist, cx));
        self.close(window, cx);
    }

    fn delete(&mut self, window: &mut Window, cx: &mut Context<Self>) {
        if let Some(playlist) = self.editing.clone() {
            let smart = Encore::global(cx).smart.clone();
            smart.update(cx, |smart, cx| smart.delete(&playlist.id, cx));
        }
        self.close(window, cx);
    }

    fn rule_row(&self, index: usize, cx: &mut Context<Self>) -> gpui::AnyElement {
        let row = &self.rows[index];
        let field = row.rule.field;
        div()
            .id(("smart-rule", row.id))
            .role(Role::ListItem)
            .aria_label(t!("smart-rule-label", number = index + 1))
            .aria_position_in_set(index + 1)
            .aria_size_of_set(self.rows.len())
            .flex()
            .items_center()
            .gap_2()
            .child(
                Button::new(("smart-field", row.id))
                    .small()
                    .outline()
                    .label(i18n::lookup(field.key(), None))
                    .tooltip("smart-field-next")
                    .on_click(cx.listener(move |this, _, _, cx| this.next_field(index, cx))),
            )
            .child(
                Button::new(("smart-op", row.id))
                    .small()
                    .ghost()
                    .label(i18n::lookup(row.rule.op.key(), None))
                    .tooltip("smart-op-next")
                    .disabled(field.ops().len() < 2)
                    .on_click(cx.listener(move |this, _, _, cx| this.next_op(index, cx))),
            )
            .child(
                div()
                    .flex_1()
                    .min_w_0()
                    .when(field != RuleField::Lossless, |this| {
                        this.child(row.value.clone())
                    }),
            )
            .child(
                Button::new(("smart-remove", row.id))
                    .small()
                    .ghost()
                    .icon("icons/x.svg")
                    .tooltip("smart-remove-rule")
                    .on_click(cx.listener(move |this, _, _, cx| {
                        if index < this.rows.len() {
                            this.rows.remove(index);
                        }
                        cx.notify();
                    })),
            )
            .into_any_element()
    }
}

impl Render for SmartEditor {
    fn render(&mut self, window: &mut Window, cx: &mut Context<Self>) -> impl IntoElement {
        let Some(editing) = self.editing.clone() else {
            if let Some(focus) = self.restore.take() {
                window.focus(&focus, cx);
            }
            return div().into_any_element();
        };
        let theme = *cx.theme();
        let rules: Vec<_> = (0..self.rows.len())
            .map(|index| self.rule_row(index, cx))
            .collect();
        let label = |key: &'static str| {
            div()
                .text_size(theme.text(Text::Small))
                .text_color(theme.muted_foreground)
                .child(i18n::lookup(key, None))
        };
        let all = editing.all;

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
                Modal::new("smart-editor", t!("smart-edit-title"))
                    .w(theme.metrics.cover * 3.6)
                    .h(theme.metrics.cover * 4.)
                    .child(
                        div()
                            .flex()
                            .flex_col()
                            .gap_1()
                            .child(label("smart-name"))
                            .child(self.name.clone()),
                    )
                    .child(
                        div()
                            .flex()
                            .items_center()
                            .gap_2()
                            .child(label("smart-match"))
                            .child(
                                Button::new("smart-match-all")
                                    .small()
                                    .map(|button| match all {
                                        true => button.primary(),
                                        false => button.ghost(),
                                    })
                                    .label(t!("smart-match-all"))
                                    .on_click(cx.listener(|this, _, _, cx| {
                                        if let Some(editing) = &mut this.editing {
                                            editing.all = true;
                                        }
                                        cx.notify();
                                    })),
                            )
                            .child(
                                Button::new("smart-match-any")
                                    .small()
                                    .map(|button| match all {
                                        true => button.ghost(),
                                        false => button.primary(),
                                    })
                                    .label(t!("smart-match-any"))
                                    .on_click(cx.listener(|this, _, _, cx| {
                                        if let Some(editing) = &mut this.editing {
                                            editing.all = false;
                                        }
                                        cx.notify();
                                    })),
                            ),
                    )
                    .child(
                        ui::Scroller::new("smart-editor-rules", &self.scrollbar)
                            .flex_1()
                            .child(
                                div()
                                    .id("smart-rules")
                                    .role(Role::List)
                                    .aria_label(t!("smart-rules"))
                                    .flex()
                                    .flex_col()
                                    .gap_1()
                                    .children(rules)
                                    .child(
                                        div().flex().child(
                                            Button::new("smart-add-rule")
                                                .small()
                                                .ghost()
                                                .icon("icons/plus.svg")
                                                .label(t!("smart-add-rule"))
                                                .on_click(cx.listener(|this, _, _, cx| {
                                                    this.push(Rule::new(RuleField::Title), cx);
                                                    cx.notify();
                                                })),
                                        ),
                                    ),
                            ),
                    )
                    .child(
                        div()
                            .flex()
                            .items_center()
                            .gap_2()
                            .child(label("smart-limit"))
                            .child(div().w_24().child(self.limit.clone())),
                    )
                    .when(self.saved, |modal| {
                        modal.action(
                            Button::new("delete-smart")
                                .destructive()
                                .label(t!("common-delete"))
                                .on_click(
                                    cx.listener(|this, _, window, cx| this.delete(window, cx)),
                                ),
                        )
                    })
                    .action(
                        Button::new("cancel-smart")
                            .ghost()
                            .label(t!("common-cancel"))
                            .on_click(cx.listener(|this, _, window, cx| this.close(window, cx))),
                    )
                    .action(
                        Button::new("save-smart")
                            .primary()
                            .label(t!("common-save"))
                            .on_click(cx.listener(|this, _, window, cx| this.save(window, cx))),
                    )
                    .on_dismiss(cx.listener(|this, _, window, cx| this.close(window, cx))),
            )
            .into_any_element()
    }
}
