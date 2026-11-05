# Rust-core spike

Can the Android app play through the desktop's Rust engine (`music::engine`) instead of Media3?
For one local file, yes.

## What was built

- `crates/android-core`: a `cdylib` (`libencore_core.so`) exposing `init`, `play` and `stop`
  over JNI. It starts `music::local::FilePlayback`, the same engine the desktop uses for files,
  and hands it a path. On every other platform the crate is empty.
- `android/app/src/debug`: `RustCore` (the JNI bindings) and `RustCoreActivity`, a developer
  screen that plays the first song MediaStore lists. Release builds carry neither.
- `scripts/build-android-core.sh`: builds the library and copies it into the debug APK's
  `jniLibs`, which git ignores.

## How to try it

```sh
brew install --cask android-ndk
rustup target add aarch64-linux-android
./scripts/build-android-core.sh
./android/gradlew -p android :app:installDebug
adb shell am start -n dev.scorpiontaj.encore/.core.RustCoreActivity
```

## Findings

- **It plays.** On an arm64 Android 15 emulator, the engine opened the file, decoded the MP3
  with Symphonia, and output through `cpal`'s AAudio backend: the system lists an AAudio
  player owned by the app while it plays.
- **The whole `music` crate cross-compiles** with NDK r30 at API 29. The only native build
  that needed care is libopus, which `opusic-sys` builds with CMake: it needs the NDK's C++
  compiler and its CMake toolchain file (the script sets both). SQLite and `ring` build with
  the NDK's clang as is.
- **Size:** the release library is about 11 MB for arm64 alone, before APK compression. Only
  the playback path is used, so most of `music` (lyrics sources, scrobbling, the local index)
  is dead weight here; a feature-gated `music` would shrink it.
- **Context:** `cpal` reaches Android's audio APIs through `ndk-context`, so the app hands its
  context to Rust once (`RustCore.init`) before playing.

## What a real switch would still need

- A Media3 `Player` (or `SimpleBasePlayer`) facade over the engine, so the media session,
  notification, widgets and Bluetooth controls keep working.
- Content URIs: the spike opens a file path from MediaStore's `DATA` column. Scoped storage
  favours `content://` URIs, which would need a `Fetch` that reads from a file descriptor.
- Engine events (position, track end, errors) bridged back to Kotlin; the spike drops them.
- Builds for the other ABIs, and CI that runs the script, before release builds could carry it.
