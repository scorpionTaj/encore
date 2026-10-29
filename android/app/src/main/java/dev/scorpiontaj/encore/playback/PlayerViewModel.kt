package dev.scorpiontaj.encore.playback

import android.app.Application
import android.content.ComponentName
import android.database.ContentObserver
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
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
import dev.scorpiontaj.encore.library.Library
import dev.scorpiontaj.encore.library.Song
import dev.scorpiontaj.encore.radio.RadioBrowser
import dev.scorpiontaj.encore.radio.Station
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

/** Holds the library, the radio list and the connection to [PlaybackService]. */
class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val _library = MutableStateFlow<Library?>(null)
    val library: StateFlow<Library?> = _library.asStateFlow()

    private val _radio = MutableStateFlow<RadioState>(RadioState.Idle)
    val radio: StateFlow<RadioState> = _radio.asStateFlow()

    private val _player = MutableStateFlow(PlayerState())
    val player: StateFlow<PlayerState> = _player.asStateFlow()

    private var controller: MediaController? = null
    private var libraryJob: Job? = null
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
    }

    /** Current playback position, read on demand since it changes continuously. */
    val positionMs: Long get() = controller?.currentPosition ?: 0

    fun loadLibrary(debounce: Boolean = false) {
        libraryJob?.cancel()
        libraryJob = viewModelScope.launch {
            if (debounce) delay(LIBRARY_DEBOUNCE_MS)
            val resolver = getApplication<Application>().contentResolver
            _library.value = withContext(Dispatchers.IO) { runCatching { Library.load(resolver) }.getOrNull() }
                ?: Library(emptyList(), emptyList(), emptyList(), emptyList())
        }
    }

    fun loadRadio() {
        if (_radio.value is RadioState.Loading || _radio.value is RadioState.Loaded) return
        _radio.value = RadioState.Loading
        viewModelScope.launch {
            _radio.value = runCatching { RadioBrowser.moroccanStations() }
                .fold(onSuccess = { RadioState.Loaded(it) }, onFailure = { RadioState.Failed })
        }
    }

    fun retryRadio() {
        _radio.value = RadioState.Idle
        loadRadio()
    }

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
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun skipTo(index: Int) {
        val c = controller ?: return
        c.seekToDefaultPosition(index)
        c.play()
    }

    fun removeFromQueue(index: Int) = controller?.removeMediaItem(index)

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
    }

    private companion object {
        const val LIBRARY_DEBOUNCE_MS = 750L
        const val ARTWORK_MAX_PX = 1024
    }
}
