# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Sort Android songs by date modified, and see when a song was added and modified in its details.
- Smart playlists, a Folders page, recently played and the No cover filter on Android.
- A sleep timer on Android, stopping after a set time or at the end of the song.
- Loudness normalisation from ReplayGain tags and shuffle by album on Android.
- Record the radio station playing on Android to Music, Encore Recordings.
- Open audio files in Encore for Android from other apps.
- Pick a light, dark or system theme on Android, or use your wallpaper's colours.
- Hide folders on Android from the Folders page to keep their songs out of your library, and show
  them again from Settings.

## [1.6.0]

### Added

- Smart playlists: songs gather by rules on title, artist, album, genre, format, length or when
  they were added, and follow the library as it changes.
- Browse your library by its folders on disk from the Folders page.
- Shuffle by album, in Settings, Playback.
- Record the radio station playing to a file in Music, Encore Recordings.
- Edit a song's lyrics and sync each line to the music, saved as an .lrc file beside the song.
- Encore for Android: play the music on your phone by song, album, artist and playlist, with a
  queue, shuffle and repeat.
- A 5×1 home-screen widget on Android with the cover, shuffle, previous, play, next and repeat.
- Radio on Android from Morocco and the other countries, with favourites and the song each
  station is playing.
- Listening stats, synced lyrics, an equalizer, search, song details and the library filters on
  Android.
- Encore for Android updates itself from the same releases as the Mac app.

## [1.5.0]

### Added

- A Stats page with how much you listened and your top songs and artists, by week, month, year
  or all time.
- Mark radio stations as favourites with their heart, and they stay at the top.
- Listen to radio from other countries, picked on the Radio page.
- Radio stations that say which song is on show it under the station's name.
- Filter your songs to the ones missing a cover, album or artist, or to duplicates found in more
  than one file.
- Songs you add to or remove from your music folders show up in the library by themselves. Turn it
  off with Watch for new music in Settings, Library, Music folders.

## [1.4.0]

### Added

- A song's details show its year, genres, label, language and composer from the file's tags.
- Right-click a list's column headers to show or hide columns. The Columns button is gone.
- Install themes shared as links, including the ones on sonorahq.org/themes, with one click.
- Explore community themes from Settings, Appearance, Theme.
- Delete a theme you added with the trash button on its card.

## [1.3.0]

### Added

- More Moroccan radio: Medi 1 Radio, Monte Carlo Doualiya, Urban Hit Maroc and 4U Classic Rock.
- Logos for Radio Mars, Med Radio, Medi 1, Hit Radio, Cap Radio, Aswat and other stations.
- Check for Updates in the Encore menu.
- The update card lists what changed in the new version.
- A designed install window in the disk image.

### Changed

- Encore is a Mac app only.
- Scrobbling to Last.fm, ListenBrainz, Libre.fm and Maloja is gone.

### Fixed

- Stations listed twice under different names show once.

## [1.1.1]

### Fixed

- The Radio page shows every station instead of only the first row.
- Live radio no longer shows a 0:00 length or a seek bar.

## [1.1.0]

### Added

- A Radio page that plays Moroccan radio stations from Radio Browser.
- A Format column showing each song's codec and quality, and a filter for lossless files.
- Crossfade between songs, set from 2 to 8 seconds in the playback settings.
- A floating mini player that stays on top of other windows.
- Arabic translation.

### Fixed

- The credits list on the About page builds again.

## [1.0.0]

### Added

- A Date modified column in the songs list, sortable like the others.
- A new app icon.
- Encore updates itself from its own GitHub releases.

### Changed

- Encore plays local files only: there are no streaming services, sign-in, downloads, Spotify
  Connect, DRM module or install counter.
- The library, home and search show your local music, and history is kept without an account.
