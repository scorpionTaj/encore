# encore

A native player for local music files, built with Rust and
[GPUI](https://github.com/zed-industries/zed) (Zed's UI framework), licensed GPL-3.0-or-later.
Cargo workspace, edition 2024, resolver 3.

## Crate layout

```
crates/
  encore/     binary: main, window, actions, asset registry, HTTP client shim, tray, dock
  views/      screens, shells (Workspace, FullscreenView) and app chrome
  state/      GPUI entities holding app state, and all async orchestration
  music/      provider traits and models in the root, the local provider, lyrics and scrobbling
              sources (no GPUI)
  storage/    the state.sqlite and cache.sqlite paths, schema and connection setup
  ui/         design system: theme, metrics, layout ladder and reusable elements
  router/     Destination enum, navigation history, Link trait
  input/      text input element, global actions and keybindings
  i18n/       Fluent localization: the `t!` macro, locale selection, embedded .ftl
  icons/      the icon packs: registry, active pack, path resolution, AssetSource
  embed/      build-script helper that walks a folder and writes include_bytes! literals
```

Dependency direction is strict. Do not create a back edge:

```
encore → views → state → music
         state, music → storage
         all ui-side crates → ui, router, input → ui → gpui
         every ui-side crate → i18n, icons → gpui
```

- `state` and `views` see only the traits and models in the root of `music`, never a provider
  module. Only `encore/src/main.rs` names a concrete provider.
- `music` never depends on `gpui`. `ui` never knows about `music`, `state` or playback. Widgets
  that need app state live in `views/src/chrome/`.
- `music::engine` is the one playback engine. The local provider implements `engine::Fetch`
  rather than writing its own. The engine applies loudness normalisation, so a provider only
  reports a track's level through `Fetch::loudness`.
- There is one library, the local one. Do not add streaming providers, sign-in or DRM back.

## Rules

- **Reuse before you build.** Check `crates/ui/src/lib.rs`, `crates/views/src/shared/` and
  `crates/views/src/chrome/` first, and extend an element with a builder method rather than
  writing a sibling. New elements copy the shape of `ui/src/button.rs`, including the
  `mem::take` and `refine` of caller styles.
- **Never hardcode a color, radius or size.** Read them from `cx.theme()` and `theme.metrics`. A
  literal `px(…)` is allowed only for a local, non-scaling detail declared as a module `const`. A
  new color token goes into `Theme`, every `Theme::*()` constructor, `ThemeOverrides` and
  `apply_color!`.
- **Never write a bare breakpoint.** Use the `ui::Room` ladder, and classify width with
  `Chrome::room` or `Chrome::content`, never the raw viewport. A view whose layout depends on
  width observes `Chrome::entity(cx)`.
- **Every user-facing string comes from Fluent.** Add the key to `assets/i18n/en-US/main.ftl`. Other
  locales may lag. Never call `t!` in a constructor: store the key and resolve it in `render`.
  Counts use Fluent selectors. Log messages and `.context(…)` stay in English.
- **Every interactive element is announced by screen readers.** A node reaches the
  accessibility tree only with both `.id()` and `.role()`; pair every role with an
  `aria_label` resolved from Fluent in `render` (icon-only buttons take their name from the
  tooltip). Keep ids stable across frames and unique within a list: wrap rows in
  `div().id(("kind", stable_id))` with `aria_position_in_set`/`aria_size_of_set`, never a bare
  row index. Extend `crates/ui/tests/a11y_tree.rs` with every new primitive.
- **Network work runs on tokio.** Anything touching `MusicApi` or sockets goes inside
  `Io::global(cx).spawn`, awaited from `cx.spawn` and applied in `this.update` ending with
  `cx.notify()`. Store the returned `Task` in a field and never detach a data load. Network-backed
  features belong in a `state` entity, not a view. An entity that caches library data reloads on
  `SessionEvent::LocalChanged`.
- **Never drive a player from a view.** Go through `state::Playback`.
- **Assets are picked up from their folder.** Drop an icon into `assets/icons/<pack>/` or a face
  into `assets/fonts/`. Call sites spell `"icons/<name>.svg"` and resolve it with `icons::path` at
  render.
- **Generated files are regenerated, never edited.** App icons come from
  `scripts/generate-icons.py`, `THIRD-PARTY.md` from `scripts/generate-notices.py`, the README
  translation table from `scripts/i18n-coverage.py`.
- **Do not add tests unless asked.** Offering is fine. Existing tests are `#[cfg(test)] mod tests`
  at the bottom of the file they cover. Accessibility regression tests in
  `crates/ui/tests/a11y_tree.rs` are the exception: extend them with every new primitive. Accessibility regression tests in
  `crates/ui/tests/a11y_tree.rs` are the exception: extend them with every new primitive.

## Building and checks

```sh
cargo run --locked --package encore
cargo fmt
cargo check --workspace
cargo test --workspace
cargo clippy --workspace --all-targets
./scripts/bundle-macos.sh      # release build into dist/Encore.app
```

`gpui_platform` is built with `runtime_shaders`, so the Metal shaders compile when the app starts
and a build needs only the Xcode Command Line Tools, not full Xcode.
`.cargo/config.toml` sets the build target to `aarch64-apple-darwin`: Encore ships for Apple
Silicon Macs only, so artefacts land in `target/aarch64-apple-darwin/`. The app speaks English and
French; a new string goes into both `assets/i18n/en-US/main.ftl` and `assets/i18n/fr/main.ftl`.

Clippy is clean, `--all-targets` included, and stays that way. Boxed callback fields get a
module-local `type` alias rather than a `type_complexity` warning. The one `#[allow]` is
`reversed_empty_ranges` on the `clamp_range("abc", &(2..1))` test in `crates/ui/src/input/mod.rs`.

## Code style

- Every type and every function whose behaviour is not obvious from its name carries a `///`
  comment saying what it is for and what a caller has to know. One or two plain sentences.
- `use gpui::prelude::*;` then explicit imports. Traits are imported anonymously
  (`use ui::ActiveTheme as _;`).
- Module order: `use` block, `const`s, types, impls, private free helpers at the bottom.
- In render, prefer `.when()`, `.when_some()` and `.map()` over branching. Two-arm boolean choices
  use `match flag { true => …, false => … }`. Use let-else and return early.
- `anyhow::Result` at boundaries with lowercase `.context("cannot …")`. Logs are prefixed by
  subsystem (`"playback: …"`).
- Dependencies go in the root `[workspace.dependencies]`. `gpui` and `gpui_platform` are pinned to
  one git rev and move together.

## Commits

Conventional Commits: `type(scope): description`, imperative, lowercase, no trailing period, no
body. Scopes in use: `views`, `ui`, `music`, `state`, `playback`, `player`, `settings`, `router`,
`local`, `encore`.

## Pull requests

One heading, one list, nothing else:

```markdown
## Summary

- add playlist create, rename, delete, visibility, and track mutation support
- add album and playlist queue actions
```

Bullets are lowercase, imperative, one line each, and describe behaviour rather than files.

## Changelog

`CHANGELOG.md` follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Add to
`## [Unreleased]` as features land, under `Added`, `Changed` or `Fixed`. Each entry is one short
line saying what someone using Encore can now do. Leave out implementation details and any work
no user can observe.
