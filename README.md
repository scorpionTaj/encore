<div align="center">

<img src="./assets/readme-icon.svg" alt="" width="72" height="72">

<h1>Encore</h1>

### A native music player for the music on your Mac

</div>

Encore plays the music files on your computer, in a fast native app built with Rust and
[GPUI](https://github.com/zed-industries/zed). There is no account and no streaming service,
only your own folders of music.

## Features

- Plays local files: MP3, FLAC, ALAC/AAC (M4A), Ogg Vorbis, Opus, WAV and AIFF
- Library of songs, albums, artists and playlists built from the folders you add, watched for
  changes, with tag editing and Date added and Date modified columns
- Gapless playback, loudness normalisation, an equalizer, shuffle, repeat and a sleep timer
- Synced and karaoke lyrics from `.lrc` files beside your music or from LrcLib, Musixmatch,
  Kugou and NetEase, with romanization
- Discord Rich Presence, media keys and Now Playing, opening files from Finder
- Themes, fonts, icon packs, transparency and blur
- English and French
- Updates itself from this repository's releases

## Installing

Encore runs on Apple Silicon Macs (M1 and later) with macOS 11 or newer.

1. Download `Encore-<version>-macos-arm64.dmg` from the
   [latest release](https://github.com/scorpionTaj/encore/releases/latest).
2. Open it and drag **Encore** into **Applications**.
3. Encore is not notarized by Apple, so the first time, run this in Terminal before opening it:

   ```sh
   xattr -dr com.apple.quarantine /Applications/Encore.app
   ```

After that, Encore tells you when a new release is out and installs it for you.

## Building

You need Rust (the version pinned in `rust-toolchain.toml` installs itself through
[rustup](https://rustup.rs)) and the Xcode Command Line Tools.

```sh
cargo run --release --package encore
```

To build `dist/Encore.app`:

```sh
./scripts/bundle-macos.sh
```

## License

Encore is free software under the GNU General Public License, version 3 or later. See
[COPYING](./COPYING), and [THIRD-PARTY.md](./THIRD-PARTY.md) for third-party notices. It was
originally forked from [Sonora](https://github.com/sonorahq/sonora).
