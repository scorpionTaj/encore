package dev.scorpiontaj.encore.playback

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** When playback stops by itself: at a moment, at the end of the current song, or never. */
sealed interface SleepState {
    data object Off : SleepState
    /** [atMs] is on the [SystemClock.elapsedRealtime] clock. */
    data class At(val atMs: Long) : SleepState
    data object EndOfSong : SleepState
}

/**
 * The sleep timer. PlaybackService attaches its player; the now playing screen sets the timer.
 * Both run in the app's one process. The timer pauses the player and switches itself off.
 */
@androidx.annotation.OptIn(UnstableApi::class)
object Sleep {
    private val handler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private val _state = MutableStateFlow<SleepState>(SleepState.Off)
    val state: StateFlow<SleepState> = _state.asStateFlow()

    private val fire = Runnable {
        player?.pause()
        cancel()
    }

    fun attach(player: ExoPlayer) {
        this.player = player
    }

    fun detach() {
        cancel()
        player = null
    }

    fun after(minutes: Int) {
        cancel()
        val delay = minutes * 60_000L
        // The player holds a wake lock while it plays, so the handler's clock keeps running.
        handler.postDelayed(fire, delay)
        _state.value = SleepState.At(SystemClock.elapsedRealtime() + delay)
    }

    fun atEndOfSong() {
        cancel()
        player?.pauseAtEndOfMediaItems = true
        _state.value = SleepState.EndOfSong
    }

    fun cancel() {
        handler.removeCallbacks(fire)
        player?.pauseAtEndOfMediaItems = false
        _state.value = SleepState.Off
    }

    /** Called by PlaybackService when the player paused at the end of a song on its own. */
    fun onPausedAtEndOfSong() {
        if (_state.value == SleepState.EndOfSong) cancel()
    }
}
