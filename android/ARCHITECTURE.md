# Encore for Android — architecture

The Android app is a native Kotlin + Jetpack Compose + Media3 player. It shares Encore's name,
icon, colours, themes, fonts, version and CHANGELOG with the desktop app, but no code — the
desktop is Rust + GPUI under `crates/`. This file is the map a new contributor needs.

## Layout

```
android/app/src/main/java/dev/scorpiontaj/encore/
  MainActivity.kt        the one activity: tabs, pages, details, dialogs, back stack, transitions
  library/               everything MediaStore and files, no Compose
    Library.kt           reads MediaStore into Library/Song/Album/Artist, hidden-folder filtering
    SmartPlaylists.kt    rules, evaluation against a Library, JSON persistence
    Lyrics.kt            LRC read/write, .lrc files beside songs, online sources
    History.kt           listening history and stats
    PlaylistEditor.kt    .m3u playlists
    TagEditor.kt         tag write-back through the storage provider
  playback/
    PlaybackService.kt   the one MediaSessionService: players, crossfade, history, widget commands
    PlayerViewModel.kt   the state the screens read: player mirror, library, radio, updates
    Sound.kt             equalizer, bass boost and surround on the player's audio session
    Sleep.kt             the sleep timer
  radio/                 radio browser, playback of stations, recording
  settings/              SettingsState (shared prefs), theme files
  ui/                    Compose screens and the design system pieces they share
  update/                GitHub releases, APK download and PackageInstaller install
  widget/                the home-screen widgets, fed by the service
```

## The rules

- **One player, one session.** `PlaybackService` owns the ExoPlayers and the `MediaSession`;
  nothing else touches them. The app and the widgets reach playback through a `MediaController`,
  and the widget's buttons come back as service intents. Crossfade uses a second, preloaded
  player that never requests audio focus; it takes the focus over when it becomes the session
  player.
- **Screens read state, the service changes it.** A screen never calls a player; it calls
  `PlayerViewModel`, which calls the controller. State mirrors back through
  `collectAsStateWithLifecycle`.
- **The library belongs to MediaStore.** Songs are read from MediaStore, never scanned by hand.
  Hidden folders filter whole path segments (`Library.isUnder`), and a ContentObserver reloads
  the library when MediaStore changes.
- **Every user-facing string is a resource.** `values/strings.xml`, then the same key in
  `values-fr` and `values-ar`; `scripts/i18n-coverage-android.py` (from the repository root)
  fails when a locale is missing a string another locale carries.
- **Settings live in `SettingsState`.** One immutable data class in `settings/Settings.kt`, read
  by the screens and the service alike, persisted as shared preferences.
- **Unit tests live in `app/src/test`** for the pure logic — rule evaluation, LRC parsing,
  folder matching, version comparison — and run in CI together with lint.

## Crossfade, in one paragraph

While a song plays, a second player is preloaded with the queue as it will play, paused at the
next item, sharing the first player's audio session so the equalizer covers both. When the
current song enters the fade window the second player starts and the two volumes ramp in
opposite directions on a 50 ms tick; when the first reaches its natural end the session is
switched (`MediaSession.setPlayer`) to the second player, which already holds the queue. Skips,
seeks and queue edits during the fade cancel it; the sleep timer's end-of-song mode keeps a fade
from starting at all.
