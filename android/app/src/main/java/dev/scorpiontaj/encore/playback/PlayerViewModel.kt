package dev.scorpiontaj.encore.playback

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.database.ContentObserver
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
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
import dev.scorpiontaj.encore.library.Lrclib
import dev.scorpiontaj.encore.library.Lyrics
import dev.scorpiontaj.encore.library.Period
import dev.scorpiontaj.encore.library.Song
import dev.scorpiontaj.encore.library.Summary
import dev.scorpiontaj.encore.radio.RadioBrowser
import dev.scorpiontaj.encore.radio.RadioPrefs
import dev.scorpiontaj.encore.radio.Station
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

    private val _lyrics = MutableStateFlow<LyricsState>(LyricsState.Idle)
    val lyrics: StateFlow<LyricsState> = _lyrics.asStateFlow()

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Quiet)
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    val equalizer: StateFlow<EqualizerState> = Sound.state

    private var controller: MediaController? = null
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
            if (_library.value != null) loadLibrary(debounce = true)
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
        }
        app.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
        checkForUpdates(asked = false)
    }

    /** Current playback position, read on demand since it changes continuously. */
    val positionMs: Long get() = controller?.currentPosition ?: 0

    fun loadLibrary(debounce: Boolean = false) {
        libraryJob?.cancel()
        libraryJob = viewModelScope.launch {
            if (debounce) delay(LIBRARY_DEBOUNCE_MS)
            val resolver = getApplication<Application>().contentResolver
            _library.value = withContext(Dispatchers.IO) {
                runCatching { Library.load(resolver) }.onFailure { Log.w(TAG, "library: cannot read MediaStore", it) }.getOrNull()
            } ?: Library(emptyList(), emptyList(), emptyList(), emptyList())
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
    fun loadLyrics(song: Song?) {
        val mediaId = song?.toMediaItem()?.mediaId
        val current = _lyrics.value
        if (current is LyricsState.Found && current.mediaId == mediaId) return
        lyricsJob?.cancel()
        if (song == null) {
            _lyrics.value = LyricsState.Missing
            return
        }
        _lyrics.value = LyricsState.Loading
        lyricsJob = viewModelScope.launch {
            val found = runCatching { Lrclib.find(song) }.onFailure { Log.w(TAG, "lyrics: lookup failed", it) }.getOrNull()
            _lyrics.value = found?.let { LyricsState.Found(mediaId!!, it) } ?: LyricsState.Missing
        }
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
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
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
        c.shuffleModeEnabled = shuffle
        val first = if (shuffle) songs.indices.random() else start
        c.setMediaItems(songs.map(Song::toMediaItem), first, 0)
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
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(listOf(song))
        c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
    }

    fun addToQueue(song: Song) {
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

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun sync(player: Player) {
        val metadata = player.mediaMetadata
        if (!metadata.artworkData.contentEquals(artworkData)) {
            artworkData = metadata.artworkData
            artwork = artworkData?.let { data ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= ARTWORK_MAX_PX) sample *= 2
                BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?.asImageBitmap()
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
