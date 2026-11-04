package dev.scorpiontaj.encore.playback

import android.app.Application
import android.content.ComponentName
import android.content.ContentResolver
import android.content.IntentSender
import android.content.Intent
import android.database.ContentObserver
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.scorpiontaj.encore.library.History
import dev.scorpiontaj.encore.library.Library
import dev.scorpiontaj.encore.library.LyricLine
import dev.scorpiontaj.encore.library.LyricsFiles
import dev.scorpiontaj.encore.library.OnlineLyrics
import dev.scorpiontaj.encore.library.Playlist
import dev.scorpiontaj.encore.library.PlaylistEditor
import dev.scorpiontaj.encore.library.TagEditor
import dev.scorpiontaj.encore.library.TagWrite
import dev.scorpiontaj.encore.library.Tags
import dev.scorpiontaj.encore.library.Lyrics
import dev.scorpiontaj.encore.library.Period
import dev.scorpiontaj.encore.library.SmartPlaylist
import dev.scorpiontaj.encore.library.SmartPlaylistStore
import dev.scorpiontaj.encore.library.TopSong
import dev.scorpiontaj.encore.library.Song
import dev.scorpiontaj.encore.library.Summary
import dev.scorpiontaj.encore.radio.RadioBrowser
import dev.scorpiontaj.encore.radio.RadioPrefs
import dev.scorpiontaj.encore.radio.Recorder
import dev.scorpiontaj.encore.radio.Recording
import dev.scorpiontaj.encore.radio.Station
import dev.scorpiontaj.encore.settings.Settings
import dev.scorpiontaj.encore.settings.SettingsState
import dev.scorpiontaj.encore.settings.ThemeFile
import dev.scorpiontaj.encore.settings.Themes
import dev.scorpiontaj.encore.R
import dev.scorpiontaj.encore.update.Release
import dev.scorpiontaj.encore.update.Updates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the player is doing, mirrored from the MediaController for Compose. */
data class PlayerState(
    val mediaId: String? = null,
    val metadata: MediaMetadata = MediaMetadata.EMPTY,
    val artwork: ImageBitmap? = null,
    val playing: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val durationMs: Long = 0,
    val seekable: Boolean = false,
    val queue: List<MediaItem> = emptyList(),
    val index: Int = 0,
) {
    val hasMedia: Boolean get() = mediaId != null
}

sealed interface RadioState {
    data object Idle : RadioState
    data object Loading : RadioState
    data object Failed : RadioState
    data class Loaded(val stations: List<Station>) : RadioState
}

sealed interface LyricsState {
    data object Idle : LyricsState
    data object Loading : LyricsState
    data object Missing : LyricsState
    data class Found(val mediaId: String, val lyrics: Lyrics) : LyricsState
}

/** A short message for the snackbar: a string resource and the values it names. */
data class Notice(val message: Int, val args: List<Any> = emptyList())

sealed interface UpdateState {
    data object Quiet : UpdateState
    data object Checking : UpdateState
    data object Current : UpdateState
    data class Offered(val release: Release) : UpdateState
    data object Downloading : UpdateState
    data object Failed : UpdateState
}

/** Holds the library, radio, stats, lyrics, updates and the connection to [PlaybackService]. */
class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val radioPrefs = RadioPrefs(app)
    private val history = History(app)
    private val smartStore = SmartPlaylistStore(app)

    init {
        Settings.init(app)
    }

    val settings: StateFlow<SettingsState> = Settings.state
    val sleep: StateFlow<SleepState> = Sleep.state
    val recording: StateFlow<Recording?> = Recorder.state

    private val _smartPlaylists = MutableStateFlow(smartStore.load())
    val smartPlaylists: StateFlow<List<SmartPlaylist>> = _smartPlaylists.asStateFlow()

    private val _recent = MutableStateFlow<List<TopSong>?>(null)
    val recent: StateFlow<List<TopSong>?> = _recent.asStateFlow()

    /** Ids of library songs with no embedded cover, worked out when the No cover filter is first used. */
    private val _missingCovers = MutableStateFlow<Set<Long>?>(null)
    val missingCovers: StateFlow<Set<Long>?> = _missingCovers.asStateFlow()
    private var coversJob: Job? = null

    private val _library = MutableStateFlow<Library?>(null)
    val library: StateFlow<Library?> = _library.asStateFlow()

    private val _radio = MutableStateFlow<RadioState>(RadioState.Idle)
    val radio: StateFlow<RadioState> = _radio.asStateFlow()

    private val _country = MutableStateFlow(radioPrefs.country)
    val country: StateFlow<String> = _country.asStateFlow()

    private val _favourites = MutableStateFlow(radioPrefs.favourites)
    val favourites: StateFlow<Set<String>> = _favourites.asStateFlow()

    private val _player = MutableStateFlow(PlayerState())
    val player: StateFlow<PlayerState> = _player.asStateFlow()

    private val _period = MutableStateFlow(Period.Month)
    val period: StateFlow<Period> = _period.asStateFlow()

    private val _summary = MutableStateFlow<Summary?>(null)
    val summary: StateFlow<Summary?> = _summary.asStateFlow()

    private val _themes = MutableStateFlow(Themes.all(app))
    val themes: StateFlow<List<ThemeFile>> = _themes.asStateFlow()

    /** A theme from a link, waiting for the person to install it or not. */
    private val _offeredTheme = MutableStateFlow<ThemeFile?>(null)
    val offeredTheme: StateFlow<ThemeFile?> = _offeredTheme.asStateFlow()

    /** A short message for the snackbar, as a string resource. */
    private val _notice = MutableStateFlow<Notice?>(null)
    val notice: StateFlow<Notice?> = _notice.asStateFlow()

    /** The size of Encore's cache folder, shown in Settings → Storage. */
    private val _cacheBytes = MutableStateFlow(0L)
    val cacheBytes: StateFlow<Long> = _cacheBytes.asStateFlow()

    private val _lyrics = MutableStateFlow<LyricsState>(LyricsState.Idle)
    val lyrics: StateFlow<LyricsState> = _lyrics.asStateFlow()

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Quiet)
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    val equalizer: StateFlow<EqualizerState> = Sound.state

    private var controller: MediaController? = null
    private var artworkJob: Job? = null

    /** A file opened before the controller connected, played once it does. */
    private var pendingUri: Uri? = null

    /**
     * What is waiting on a system prompt: the song Android's delete confirmation is about, and
     * the tag and lyrics writes waiting for the storage provider's blessing. Held here so a
     * rotation does not lose the approval that arrives after it.
     */
    var pendingDelete: Song? = null
    var pendingTags: Pair<Song, Tags>? = null
    var pendingLyrics: Pair<Song, List<LyricLine>>? = null

    /** The storage provider's permission request to relaunch, once one is pending. */
    var pendingWriteRequest: IntentSender? = null
    private var libraryJob: Job? = null
    private var radioJob: Job? = null
    private var lyricsJob: Job? = null
    private var artworkData: ByteArray? = null
    private var artwork: ImageBitmap? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)
    }

    /** Reloads the library when MediaStore changes, a beat after the last change. */
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (_library.value != null && Settings.state.value.watchFolders) loadLibrary(debounce = true)
        }
    }

    init {
        viewModelScope.launch {
            val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
            val connected = runCatching { MediaController.Builder(app, token).buildAsync().await() }.getOrNull()
                ?: return@launch
            controller = connected
            connected.addListener(listener)
            sync(connected)
            pendingUri?.let { pendingUri = null; playUri(it) }
        }
        app.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
        if (Settings.state.value.checkUpdates) checkForUpdates(asked = false)
    }

    /** Current playback position, read on demand since it changes continuously. */
    val positionMs: Long get() = controller?.currentPosition ?: 0

    fun loadLibrary(debounce: Boolean = false) {
        libraryJob?.cancel()
        libraryJob = viewModelScope.launch {
            if (debounce) delay(LIBRARY_DEBOUNCE_MS)
            val resolver = getApplication<Application>().contentResolver
            _library.value = withContext(Dispatchers.IO) {
                runCatching { Library.load(resolver, Settings.state.value.hiddenFolders) }.onFailure { Log.w(TAG, "library: cannot read MediaStore", it) }.getOrNull()
            } ?: Library(emptyList(), emptyList(), emptyList(), emptyList())
            _missingCovers.value = null
        }
    }

    /** Finds the songs without a cover by asking MediaStore for each song's thumbnail. */
    fun findMissingCovers() {
        val songs = _library.value?.songs ?: return
        if (coversJob?.isActive == true || _missingCovers.value != null) return
        val resolver = getApplication<Application>().contentResolver
        coversJob = viewModelScope.launch {
            _missingCovers.value = withContext(Dispatchers.IO) {
                songs.filter { song ->
                    runCatching { resolver.loadThumbnail(song.uri, android.util.Size(32, 32), null).recycle() }.isFailure
                }.mapTo(mutableSetOf()) { it.id }
            }
        }
    }

    // Settings, sleep timer and recording

    fun updateSettings(change: (SettingsState) -> SettingsState) = Settings.update(change)

    /** Keeps [folder]'s songs, and its subfolders' songs, out of every list until shown again. */
    fun hideFolder(folder: String) {
        // Store the path the way [Library] matches it: segments separated by one trailing slash.
        val path = folder.trim('/').let { if (it.isEmpty()) return else "$it/" }
        showNotice(R.string.notice_folder_hidden, path.trimEnd('/'))
        Settings.update { it.copy(hiddenFolders = it.hiddenFolders + path) }
        loadLibrary()
    }

    fun showFolder(folder: String) {
        Settings.update { it.copy(hiddenFolders = it.hiddenFolders - folder) }
        loadLibrary()
    }

    fun sleepAfter(minutes: Int) {
        Sleep.after(minutes)
        showNotice(R.string.notice_sleep_set, minutes)
    }

    fun sleepAtEndOfSong() {
        Sleep.atEndOfSong()
        showNotice(R.string.sleep_stops_after_song)
    }

    fun cancelSleep() = Sleep.cancel()

    fun startRecording(station: Station) = Recorder.start(getApplication(), station)

    fun stopRecording() {
        Recorder.stop()
        showNotice(R.string.notice_recording_saved)
    }

    // Smart playlists

    fun saveSmartPlaylist(playlist: SmartPlaylist) {
        showNotice(R.string.notice_playlist_saved, playlist.name)
        val updated = _smartPlaylists.value.filterNot { it.id == playlist.id } + playlist
        smartStore.save(updated)
        _smartPlaylists.value = updated
    }

    fun deleteSmartPlaylist(playlist: SmartPlaylist) {
        val updated = _smartPlaylists.value.filterNot { it.id == playlist.id }
        smartStore.save(updated)
        _smartPlaylists.value = updated
    }

    // History

    fun loadRecent() {
        viewModelScope.launch {
            _recent.value = withContext(Dispatchers.IO) { runCatching { history.recent() }.getOrDefault(emptyList()) }
        }
    }

    // Radio

    fun loadRadio() {
        if (_radio.value is RadioState.Loading || _radio.value is RadioState.Loaded) return
        _radio.value = RadioState.Loading
        radioJob?.cancel()
        radioJob = viewModelScope.launch {
            _radio.value = runCatching { RadioBrowser.stations(_country.value) }
                .onFailure { Log.w(TAG, "radio: cannot read the station list", it) }
                .fold(onSuccess = { RadioState.Loaded(it) }, onFailure = { RadioState.Failed })
        }
    }

    fun retryRadio() {
        _radio.value = RadioState.Idle
        loadRadio()
    }

    fun pickCountry(code: String) {
        if (code == _country.value) return
        radioPrefs.country = code
        _country.value = code
        retryRadio()
    }

    fun toggleFavourite(station: Station) {
        val adding = station.uuid !in _favourites.value
        showNotice(if (adding) R.string.notice_favourite_added else R.string.notice_favourite_removed, station.name)
        val updated = _favourites.value.let { if (station.uuid in it) it - station.uuid else it + station.uuid }
        radioPrefs.favourites = updated
        _favourites.value = updated
    }

    // Stats

    fun loadStats(period: Period = _period.value) {
        _period.value = period
        viewModelScope.launch {
            _summary.value = withContext(Dispatchers.IO) { runCatching { history.summary(period) }.getOrNull() }
        }
    }

    // Lyrics

    /** Looks up lyrics for [song] unless they are already shown or on their way. */
    fun loadLyrics(song: Song?, force: Boolean = false) {
        val mediaId = song?.toMediaItem()?.mediaId
        val current = _lyrics.value
        if (!force && current is LyricsState.Found && current.mediaId == mediaId) return
        lyricsJob?.cancel()
        if (song == null) {
            _lyrics.value = LyricsState.Missing
            return
        }
        _lyrics.value = LyricsState.Loading
        lyricsJob = viewModelScope.launch {
            // The .lrc beside the song wins, as on the desktop, then the online sources.
            val tree = Settings.state.value.lyricsTree?.let(Uri::parse)
            val resolver = getApplication<Application>().contentResolver
            val found = withContext(Dispatchers.IO) { LyricsFiles.read(resolver, tree, song) }
                ?: runCatching { OnlineLyrics.find(song, Settings.state.value.lyricsSources) }.onFailure { Log.w(TAG, "lyrics: lookup failed", it) }.getOrNull()
            _lyrics.value = found?.let { LyricsState.Found(mediaId!!, it) } ?: LyricsState.Missing
        }
    }

    /**
     * Saves edited lyrics as the .lrc beside [song] and shows them. Returns false when Encore has
     * no folder holding the song yet, so the screen can ask for one and call again.
     */
    suspend fun saveLyrics(song: Song, lines: List<LyricLine>): Boolean {
        val tree = Settings.state.value.lyricsTree?.let(Uri::parse)
        if (!LyricsFiles.covers(tree, song)) return false
        val resolver = getApplication<Application>().contentResolver
        val saved = withContext(Dispatchers.IO) { runCatching { LyricsFiles.write(resolver, tree!!, song, lines) }.getOrDefault(false) }
        if (saved) {
            val synced = lines.all { it.startMs >= 0 }
            val shown = if (synced) lines.sortedBy { it.startMs } else lines
            _lyrics.value = LyricsState.Found(song.toMediaItem().mediaId, Lyrics(shown, synced, LyricsFiles.SOURCE))
        } else {
            _notice.value = Notice(R.string.notice_lyrics_failed)
        }
        return true
    }

    /** Keeps the folder the person picked for lyrics, across restarts. */
    fun grantLyricsFolder(tree: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        getApplication<Application>().contentResolver.takePersistableUriPermission(tree, flags)
        Settings.update { it.copy(lyricsTree = tree.toString()) }
    }

    // Tags

    suspend fun readTags(song: Song): Tags = TagEditor.read(getApplication(), song)

    /** Writes [tags] into [song]'s file. Returns a system prompt to show when Android wants the person's OK first. */
    suspend fun saveTags(song: Song, tags: Tags): IntentSender? {
        val result = runCatching { TagEditor.write(getApplication(), song, tags) }
            .onFailure { Log.w(TAG, "tags: cannot write ${song.path}", it) }
            .getOrNull()
        return when (result) {
            is TagWrite.NeedsPermission -> result.request
            TagWrite.Saved -> {
                loadLibrary()
                _notice.value = Notice(R.string.notice_tags_saved)
                null
            }
            null -> {
                _notice.value = Notice(R.string.notice_tags_failed)
                null
            }
        }
    }

    // Playlists

    private fun editPlaylists(done: Int, vararg args: Any, action: (ContentResolver) -> Unit) {
        val resolver = getApplication<Application>().contentResolver
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { action(resolver) }.onFailure { Log.w(TAG, "playlists: change failed", it) }.isSuccess
            }
            _notice.value = if (ok) Notice(done, args.toList()) else Notice(R.string.notice_playlist_failed)
            loadLibrary()
        }
    }

    fun createPlaylist(name: String, songs: List<Song> = emptyList()) = editPlaylists(R.string.notice_playlist_created, name) { resolver ->
        val id = PlaylistEditor.create(resolver, name) ?: error("playlists: create returned nothing")
        if (songs.isNotEmpty()) PlaylistEditor.add(resolver, id, songs, emptyList())
    }

    fun renamePlaylist(playlist: Playlist, name: String) =
        editPlaylists(R.string.notice_playlist_renamed, name) { PlaylistEditor.rename(it, playlist.id, name) }

    fun deletePlaylist(playlist: Playlist) =
        editPlaylists(R.string.notice_playlist_deleted, playlist.name) { PlaylistEditor.delete(it, playlist.id) }

    fun addToPlaylist(playlist: Playlist, songs: List<Song>) =
        editPlaylists(R.string.notice_added_to_playlist, songs.singleOrNull()?.title ?: songs.size.toString(), playlist.name) {
            PlaylistEditor.add(it, playlist.id, songs, playlist.songs)
        }

    fun removeFromPlaylist(playlist: Playlist, song: Song) =
        editPlaylists(R.string.notice_removed_from_playlist, song.title, playlist.name) { PlaylistEditor.remove(it, playlist, song) }

    // Themes

    fun offerTheme(uri: Uri) {
        val theme = Themes.fromLink(uri)
        if (theme == null) _notice.value = Notice(R.string.notice_theme_invalid) else _offeredTheme.value = theme
    }

    fun dismissTheme() {
        _offeredTheme.value = null
    }

    fun installOfferedTheme() {
        val theme = _offeredTheme.value ?: return
        Themes.install(getApplication(), theme)
        _themes.value = Themes.all(getApplication())
        Settings.update { it.copy(customTheme = theme.id) }
        _offeredTheme.value = null
        showNotice(R.string.notice_theme_added, theme.name)
    }

    fun useTheme(id: String?) = Settings.update { it.copy(customTheme = id) }

    fun deleteTheme(theme: ThemeFile) {
        Themes.delete(getApplication(), theme.id)
        _themes.value = Themes.all(getApplication())
        if (Settings.state.value.customTheme == theme.id) Settings.update { it.copy(customTheme = null) }
    }

    fun showNotice(message: Int, vararg args: Any) {
        _notice.value = Notice(message, args.toList())
    }

    fun clearNotice() {
        _notice.value = null
    }

    // Song actions

    /** Shares the song's file through Android's share sheet, so it can be sent to another app or person. */
    fun shareSong(song: Song) {
        val app = getApplication<Application>()
        val send = Intent(Intent.ACTION_SEND)
            .setType(song.mime.ifEmpty { "audio/*" })
            .putExtra(Intent.EXTRA_STREAM, song.uri)
            .putExtra(Intent.EXTRA_TITLE, song.title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // The clip names the song, so the share sheet's preview shows its title.
        send.clipData = android.content.ClipData.newUri(app.contentResolver, song.title, song.uri)
        app.startActivity(Intent.createChooser(send, song.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Shares a radio station's name and stream address as text. */
    fun shareStation(station: Station) {
        val app = getApplication<Application>()
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${station.name}\n${station.url}")
        app.startActivity(Intent.createChooser(send, station.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Deletes the song's file. Android asks the person to confirm first; this returns that prompt
     * when one is needed, and the screen calls [songDeleted] once it is accepted.
     */
    suspend fun deleteSong(song: Song): IntentSender? = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            return@withContext MediaStore.createDeleteRequest(resolver, listOf(song.uri)).intentSender
        }
        try {
            resolver.delete(song.uri, null, null)
            null
        } catch (denied: android.app.RecoverableSecurityException) {
            denied.userAction.actionIntent.intentSender
        }
    }

    fun songDeleted(song: Song) {
        loadLibrary()
        showNotice(R.string.notice_song_deleted, song.title)
    }

    // Storage and general settings

    fun refreshCacheSize() {
        viewModelScope.launch {
            _cacheBytes.value = withContext(Dispatchers.IO) {
                getApplication<Application>().cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { getApplication<Application>().cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
            refreshCacheSize()
            showNotice(R.string.notice_cache_cleared)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { history.clear() }
            _summary.value = null
            _recent.value = emptyList()
            showNotice(R.string.notice_history_cleared)
        }
    }

    fun rescanLibrary() {
        loadLibrary()
        showNotice(R.string.notice_library_rescanned)
    }

    fun forgetLyricsFolder() {
        val tree = Settings.state.value.lyricsTree ?: return
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { getApplication<Application>().contentResolver.releasePersistableUriPermission(Uri.parse(tree), flags) }
        Settings.update { it.copy(lyricsTree = null) }
    }

    /** The app's language as a tag such as "fr", or null when it follows the phone. Android 13 and newer. */
    val language: String?
        get() = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            getApplication<Application>().getSystemService(android.app.LocaleManager::class.java)
                .applicationLocales.takeUnless { it.isEmpty }?.get(0)?.language
        } else {
            null
        }

    fun setLanguage(tag: String?) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        val locales = if (tag == null) android.os.LocaleList.getEmptyLocaleList() else android.os.LocaleList.forLanguageTags(tag)
        getApplication<Application>().getSystemService(android.app.LocaleManager::class.java).applicationLocales = locales
    }

    // Updates

    fun checkForUpdates(asked: Boolean) {
        if (_update.value is UpdateState.Downloading) return
        if (asked) _update.value = UpdateState.Checking
        viewModelScope.launch {
            val release = runCatching { Updates.latest() }.onFailure { Log.w(TAG, "update: check failed", it) }
            _update.value = when {
                release.getOrNull() != null -> UpdateState.Offered(release.getOrThrow()!!)
                !asked -> UpdateState.Quiet
                release.isFailure -> UpdateState.Failed
                else -> UpdateState.Current
            }
        }
    }

    fun dismissUpdate() {
        _update.value = UpdateState.Quiet
    }

    /**
     * Downloads and installs the offered release. Android first needs permission to install
     * packages from Encore; without it this opens that setting and leaves the offer up.
     */
    fun installUpdate() {
        val offered = _update.value as? UpdateState.Offered ?: return
        val app = getApplication<Application>()
        if (!app.packageManager.canRequestPackageInstalls()) {
            app.startActivity(
                Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        _update.value = UpdateState.Downloading
        viewModelScope.launch {
            runCatching {
                val apk = Updates.download(app, offered.release)
                Updates.install(app, apk)
            }.onFailure {
                Log.w(TAG, "update: install failed", it)
                _update.value = UpdateState.Failed
            }.onSuccess { _update.value = offered }
        }
    }

    // Playback

    fun play(songs: List<Song>, start: Int = 0, shuffle: Boolean = false) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        if (shuffle && Settings.state.value.shuffleByAlbum) {
            // Albums in a random order, each album's songs together and in track order.
            val byAlbum = songs.groupBy { it.albumId }.values.shuffled().flatMap { album -> album.sortedBy { it.track } }
            c.shuffleModeEnabled = false
            c.setMediaItems(byAlbum.map(Song::toMediaItem), 0, 0)
            c.prepare()
            c.play()
            return
        }
        c.shuffleModeEnabled = shuffle
        val first = if (shuffle) songs.indices.random() else start
        c.setMediaItems(songs.map(Song::toMediaItem), first, 0)
        c.prepare()
        c.play()
    }

    /** Plays an audio file another app opened Encore with. */
    fun playUri(uri: Uri) {
        val c = controller ?: run {
            pendingUri = uri
            return
        }
        val resolver = getApplication<Application>().contentResolver
        fun read(column: String) = runCatching {
            resolver.query(uri, arrayOf(column), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
        // The tag title MediaStore read, else the file name without its extension.
        val name = read(MediaStore.MediaColumns.TITLE)
            ?: read(android.provider.OpenableColumns.DISPLAY_NAME)?.substringBeforeLast('.')
        val item = MediaItem.Builder()
            .setMediaId("uri:$uri")
            .setUri(uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .setMediaMetadata(MediaMetadata.Builder().setTitle(name).build())
            .build()
        c.setMediaItem(item)
        c.prepare()
        c.play()
    }

    fun play(station: Station) {
        val c = controller ?: return
        c.setMediaItem(station.toMediaItem())
        c.prepare()
        c.play()
    }

    fun playNext(song: Song) {
        showNotice(R.string.notice_play_next, song.title)
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(listOf(song))
        c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
    }

    fun addToQueue(song: Song) {
        showNotice(R.string.notice_queued, song.title)
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(listOf(song))
        c.addMediaItem(song.toMediaItem())
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    fun togglePlay() {
        controller?.let { Util.handlePlayPauseButtonAction(it) }
    }

    fun next() = controller?.seekToNext()

    fun previous() = controller?.seekToPrevious()

    fun seekTo(positionMs: Long) = controller?.seekTo(positionMs)

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        controller?.let { it.repeatMode = nextRepeatMode(it.repeatMode) }
    }

    fun skipTo(index: Int) {
        val c = controller ?: return
        c.seekToDefaultPosition(index)
        c.play()
    }

    fun removeFromQueue(index: Int) = controller?.removeMediaItem(index)

    // Equalizer

    fun setEqualizerEnabled(enabled: Boolean) = Sound.setEnabled(getApplication(), enabled)

    fun setBandLevel(band: Int, levelMb: Int) = Sound.setLevel(getApplication(), band, levelMb)

    fun useEqualizerPreset(preset: Int) = Sound.usePreset(getApplication(), preset)

    fun setBassStrength(strength: Int) = Sound.setBass(getApplication(), strength)

    fun setSurroundStrength(strength: Int) = Sound.setSurround(getApplication(), strength)

    /** Saves the equalizer's current band levels under [name], as on the desktop. */
    fun saveEqualizerPreset(name: String) {
        val levels = Sound.state.value.bands.map { it.levelMb }
        if (levels.isEmpty()) return
        Settings.update { it.copy(equalizerPresets = it.equalizerPresets + (name to levels)) }
    }

    fun deleteEqualizerPreset(name: String) = Settings.update { it.copy(equalizerPresets = it.equalizerPresets - name) }

    fun useEqualizerPreset(name: String) {
        Settings.state.value.equalizerPresets[name]?.let { levels -> Sound.setLevels(getApplication(), levels) }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun sync(player: Player) {
        val metadata = player.mediaMetadata
        if (!metadata.artworkData.contentEquals(artworkData)) {
            artworkData = metadata.artworkData
            artwork = null
            // Decoding a large embedded cover takes long enough to hitch the frame a new song
            // starts on, so it happens off the main thread and republishes when it lands.
            artworkJob?.cancel()
            metadata.artworkData?.let { data ->
                artworkJob = viewModelScope.launch(Dispatchers.Default) {
                    val bitmap = decodeArtwork(data)
                    withContext(Dispatchers.Main) {
                        // A newer track may have taken over while this one decoded.
                        if (artworkData.contentEquals(data)) {
                            artwork = bitmap
                            controller?.let(::sync)
                        }
                    }
                }
            }
        }
        _player.value = PlayerState(
            mediaId = player.currentMediaItem?.mediaId,
            metadata = metadata,
            artwork = artwork,
            playing = !Util.shouldShowPlayButton(player),
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            durationMs = player.duration.takeIf { it > 0 } ?: 0,
            seekable = player.isCurrentMediaItemSeekable,
            queue = List(player.mediaItemCount, player::getMediaItemAt),
            index = player.currentMediaItemIndex,
        )
    }

    /** Scales an embedded cover down to at most [ARTWORK_MAX_PX] on its longest side. */
    private fun decodeArtwork(data: ByteArray): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= ARTWORK_MAX_PX) sample *= 2
        return BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?.asImageBitmap()
    }

    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(observer)
        controller?.run {
            removeListener(listener)
            release()
        }
        controller = null
        history.close()
    }

    private companion object {
        const val TAG = "encore"
        const val LIBRARY_DEBOUNCE_MS = 750L
        const val ARTWORK_MAX_PX = 1024
    }
}
