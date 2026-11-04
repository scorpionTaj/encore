package dev.scorpiontaj.encore.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.scorpiontaj.encore.MainActivity
import dev.scorpiontaj.encore.library.History
import dev.scorpiontaj.encore.library.resolved
import dev.scorpiontaj.encore.radio.RADIO_PREFIX
import dev.scorpiontaj.encore.settings.Settings
import dev.scorpiontaj.encore.widget.EncoreWidget
import dev.scorpiontaj.encore.widget.WIDGET_NEXT
import dev.scorpiontaj.encore.widget.WIDGET_PLAY_PAUSE
import dev.scorpiontaj.encore.widget.WIDGET_PREVIOUS
import dev.scorpiontaj.encore.widget.WIDGET_REPEAT
import dev.scorpiontaj.encore.widget.WIDGET_SHUFFLE
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.math.pow

/**
 * Owns the players. Media3 posts the media notification and lock-screen controls from the
 * session; the app and the widget reach the player through a MediaController. The service also
 * writes listening history, attaches the equalizer, crossfades songs into each other and shows a
 * radio stream's current song.
 *
 * There are always at most two players: [active], the one the session exposes, and [fading], a
 * second one preloaded with the next queue item while crossfade is on. When the active player
 * enters the fade window the fading player starts and the two volumes ramp in opposite
 * directions; when the active one reaches its natural end the session is switched to the fading
 * player, which by then already carries the whole queue. The sleep timer owns
 * `pauseAtEndOfMediaItems`, so a handoff never touches it and an end-of-song sleep simply keeps
 * a fade from starting.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var history: History
    private val writer = Executors.newSingleThreadExecutor()
    private val scope = MainScope()

    /** The player the session exposes, and the one preloaded with the next item during a fade. */
    private var active: ExoPlayer? = null
    private var fading: ExoPlayer? = null

    /** The item [fading] is crossfading into, non-null only while the ramp runs. */
    private var fadingItem: MediaItem? = null
    private var fadeStartMs = 0L
    private var fadeDurationMs = 0L

    /** The ReplayGain factor of the fading item, which fades in with it. */
    private var fadeGain = 1f

    /** The ReplayGain factor for the current song, which fades scale. */
    private var gain = 1f

    /** The item being listened to, and how long it has actually played. */
    private var listening: MediaItem? = null
    private var listenedMs = 0L
    private var playingSince = 0L

    /** Moves the widget's progress bar once a second while playing, and drives the fades. */
    private val ticker = Handler(Looper.getMainLooper())

    /**
     * While a crossfade ramp runs it moves both volumes; otherwise it applies the single-player
     * fade, which still fades a song's tail out when no overlap is possible, and starts a ramp
     * when the player enters the fade window.
     */
    private val fader = object : Runnable {
        override fun run() {
            val main = active ?: return
            val second = fading
            if (second != null && fadingItem != null) {
                val t = (SystemClock.elapsedRealtime() - fadeStartMs).toFloat() / fadeDurationMs
                main.volume = gain * (1f - t.coerceIn(0f, 1f))
                second.volume = fadeGain * t.coerceIn(0f, 1f)
                // The auto-advance of the main player flips the session; this is the backstop.
                if (t >= 1f) {
                    flip(main)
                    return
                }
            } else {
                applyVolume(main)
                maybeStartFade(main)
            }
            if (main.isPlaying && (fadingItem != null || Settings.state.value.crossfadeSeconds > 0)) {
                ticker.postDelayed(this, FADE_TICK_MS)
            }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            val player = active ?: return
            EncoreWidget.publishProgress(this@PlaybackService, player)
            if (player.isPlaying) ticker.postDelayed(this, PROGRESS_TICK_MS)
        }
    }

    /** The listener [player] reports through; every stateful callback ignores the fading player. */
    private fun playerListener(player: ExoPlayer) = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (player !== active) return
            if (events.containsAny(*WIDGET_EVENTS)) EncoreWidget.publish(this@PlaybackService, player)
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            if (player !== active) return
            Sound.attach(this@PlaybackService, audioSessionId)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            // The fading item's ReplayGain can only be read once it is prepared.
            if (player === fading && fadingItem != null && playbackState == Player.STATE_READY) {
                fadeGain = loudnessGain(player.currentTracks)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (player !== active) return
            if (!isPlaying && fadingItem != null) cancelFade(player)
            ticker.removeCallbacks(tick)
            tick.run()
            ticker.removeCallbacks(fader)
            fader.run()
            val now = SystemClock.elapsedRealtime()
            if (isPlaying) {
                playingSince = now
            } else if (playingSince > 0) {
                listenedMs += now - playingSince
                playingSince = 0
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (player !== active) return
            // A radio song change replaces the item in place; that is still the same listen. A
            // repeating item also arrives with its own id, but that is a fresh listen.
            if (mediaItem != null && mediaItem.mediaId == listening?.mediaId &&
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
            ) {
                listening = mediaItem
                schedulePreload(player)
                return
            }
            if (fadingItem != null) {
                // The natural end of the faded-out song hands the session over; any other
                // transition, such as a skip, abandons the half-done crossfade.
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    flip(player)
                    return
                }
                cancelFade(player)
            }
            commitListen(listenedDuration(player))
            listening = mediaItem
            listenedMs = 0
            playingSince = if (player.isPlaying) SystemClock.elapsedRealtime() else 0
            schedulePreload(player)
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (player !== active) return
            if (fadingItem != null) cancelFade(player)
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (player !== active) return
            if (fadingItem != null) {
                // The queue was edited mid-fade; the preloaded handoff is no longer trustworthy.
                cancelFade(player)
            } else {
                schedulePreload(player)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (player !== active && fadingItem != null) cancelFade(active ?: return)
        }

        override fun onMetadata(metadata: Metadata) {
            if (player !== active) return
            showStreamTitle(player, metadata)
        }

        override fun onTracksChanged(tracks: Tracks) {
            if (player !== active) return
            applyLoudness(player)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (player !== active) return
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                Sleep.onPausedAtEndOfSong()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = buildPlayer()
        active = player
        Settings.init(this)
        history = History(this)
        Sound.attach(this, player.audioSessionId)
        Sleep.attach(player)
        scope.launch { Settings.state.map { it.normalize }.distinctUntilChanged().collect {
            active?.let { applyLoudness(it) }
            // The item fading in keeps its own factor when normalisation is switched mid-fade.
            val second = fading
            if (second != null && fadingItem != null) fadeGain = loudnessGain(second.currentTracks)
        } }
        scope.launch {
            Settings.state.map { it.crossfadeSeconds }.distinctUntilChanged().collect {
                active?.let { main ->
                    if (fadingItem != null) cancelFade(main)
                    ticker.removeCallbacks(fader)
                    fader.run()
                    schedulePreload(main)
                }
            }
        }
        player.addListener(playerListener(player))
        val activity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(activity)
            .setCallback(Callback)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * The home-screen widget's buttons arrive as service intents, so they act on the player this
     * service owns without a MediaController round trip. Everything else goes to the framework.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val player = active
        when (intent?.action) {
            WIDGET_PREVIOUS -> player?.seekToPrevious()
            WIDGET_PLAY_PAUSE -> player?.let(Util::handlePlayPauseButtonAction)
            WIDGET_NEXT -> player?.seekToNext()
            WIDGET_SHUFFLE -> if (player != null) player.shuffleModeEnabled = !player.shuffleModeEnabled
            WIDGET_REPEAT -> if (player != null) player.repeatMode = nextRepeatMode(player.repeatMode)
            else -> return super.onStartCommand(intent, flags, startId)
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = active
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        ticker.removeCallbacks(tick)
        ticker.removeCallbacks(fader)
        scope.cancel()
        Sleep.detach()
        active?.let { commitListen(listenedDuration(it)) }
        Sound.release()
        writer.shutdown()
        fading?.release()
        fading = null
        active?.release()
        active = null
        session?.release()
        session = null
        EncoreWidget.publish(this, null)
        super.onDestroy()
    }

    private fun buildPlayer(handleFocus: Boolean = true): ExoPlayer = ExoPlayer.Builder(this)
        .setAudioAttributes(MUSIC_ATTRIBUTES, handleFocus)
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()

    /** Records the listen that just ended when it was long enough: 30 seconds, or half a short song. */
    private fun commitListen(durationMs: Long) {
        val item = listening ?: return
        if (playingSince > 0) {
            val now = SystemClock.elapsedRealtime()
            listenedMs += now - playingSince
            playingSince = now
        }
        val enough = listenedMs >= MIN_LISTEN_MS || (durationMs > 0 && listenedMs >= durationMs / 2)
        if (!enough) return
        val metadata = item.mediaMetadata
        val title = (metadata.station ?: metadata.title)?.toString() ?: return
        val artist = metadata.artist?.toString().takeIf { metadata.station == null }
        val listened = listenedMs
        listenedMs = 0
        writer.execute { runCatching { history.record(item.mediaId, title, artist, listened) } }
    }

    /**
     * Turns the volume down by the song's ReplayGain track gain, read from its ID3, Vorbis or MP4
     * tags, when loudness normalisation is on. Songs without the tag, and radio, play as they are.
     * Positive gains are not applied, since the player cannot go above full volume.
     */
    private fun applyLoudness(player: Player) {
        gain = loudnessGain(player.currentTracks)
        if (fadingItem == null) applyVolume(player)
    }

    /** The ReplayGain factor to apply to [tracks]: the song's own when normalisation is on. */
    private fun loudnessGain(tracks: Tracks): Float =
        if (!Settings.state.value.normalize) {
            1f
        } else {
            replayGain(tracks)?.let { 10.0.pow(it / 20).toFloat().coerceIn(0f, 1f) } ?: 1f
        }

    /**
     * Sets the volume from the ReplayGain factor and, with crossfade on, a fade at each end of
     * the song for the cases an overlap cannot cover: the last song in the queue, a song whose
     * neighbours are unknown, radio and other streams without a length.
     */
    private fun applyVolume(player: Player) {
        val fadeMs = Settings.state.value.crossfadeSeconds * 1000L
        val duration = player.duration
        val fade = if (fadeMs > 0 && duration > 2 * fadeMs && player.isCurrentMediaItemSeekable) {
            val position = player.currentPosition
            minOf(position, duration - position).toFloat().div(fadeMs).coerceIn(0f, 1f)
        } else {
            1f
        }
        player.volume = gain * fade
    }

    /**
     * Starts a crossfade when the player has entered the fade window of a song that can be
     * handed over: the preloaded player takes over while the current one fades out.
     */
    private fun maybeStartFade(main: ExoPlayer) {
        val fadeMs = Settings.state.value.crossfadeSeconds * 1000L
        if (fadeMs <= 0) return
        // A sleep timer waiting for this song to end must not be handed over to the next one.
        if (Sleep.state.value == SleepState.EndOfSong) return
        val duration = main.duration
        if (duration <= 2 * fadeMs || !main.isCurrentMediaItemSeekable) return
        if (duration - main.currentPosition > fadeMs) return
        val second = fading ?: return
        val next = nextItem(main) ?: return
        if (second.currentMediaItem?.mediaId != next.mediaId) {
            // The preloaded item went stale without a timeline event; reload it, accepting that
            // the fade-in may start a beat late on the first play.
            preload(second, main)
        }
        fadingItem = next
        fadeStartMs = SystemClock.elapsedRealtime()
        fadeDurationMs = fadeMs
        fadeGain = loudnessGain(second.currentTracks)
        second.volume = 0f
        second.play()
    }

    /**
     * Moves the session to the player that was fading in, attributing the listen of the faded-out
     * song and handing the queue, shuffle and repeat state over with it.
     */
    private fun flip(main: ExoPlayer) {
        val second = fading ?: return
        session?.player = second
        // The sleep timer's end-of-song flag lived on the player being released.
        second.pauseAtEndOfMediaItems = main.pauseAtEndOfMediaItems
        // The fading player never held the audio focus; as the session's player it must, or a
        // phone call would play over it.
        second.setAudioAttributes(MUSIC_ATTRIBUTES, true)
        Sleep.attach(second)
        active = second
        gain = fadeGain
        second.volume = fadeGain
        fading = null
        fadingItem = null
        commitListen(listenedDuration(main))
        main.release()
        listening = second.currentMediaItem
        listenedMs = 0
        playingSince = if (second.isPlaying) SystemClock.elapsedRealtime() else 0
        EncoreWidget.publish(this, second)
        ticker.removeCallbacks(tick)
        tick.run()
        ticker.removeCallbacks(fader)
        fader.run()
        schedulePreload(second)
    }

    /**
     * The duration to attribute a finished listen to. At an auto-advance the player already
     * reports the next item, so the ended item's own metadata duration comes first.
     */
    private fun listenedDuration(player: Player): Long =
        listening?.mediaMetadata?.durationMs?.takeIf { it > 0 } ?: player.duration

    /** Abandons a running or prepared crossfade, leaving [main] to play out at its own volume. */
    private fun cancelFade(main: Player) {
        fadingItem = null
        fading?.let {
            it.stop()
            it.clearMediaItems()
            it.volume = 0f
        }
        main.volume = gain
    }

    /**
     * Prepares the fading player with the item after the current one, if there is a crossfade to
     * be had: a next item, a long-enough song, no sleep timer waiting for this song to end.
     */
    private fun schedulePreload(main: ExoPlayer) {
        if (fadingItem != null) return
        // An end-of-song sleep waits for this song, so nothing may hand over to the next one.
        if (Settings.state.value.crossfadeSeconds <= 0 || Sleep.state.value == SleepState.EndOfSong) {
            clearPreload()
            return
        }
        val next = nextItem(main)
        val fadeMs = Settings.state.value.crossfadeSeconds * 1000L
        if (next == null || main.duration <= 2 * fadeMs) {
            clearPreload()
            return
        }
        val second = fading ?: createFading(main) ?: return
        preload(second, main)
    }

    /** Copies the queue, shuffle and repeat of [main] into [second], starting at the next item. */
    private fun preload(second: ExoPlayer, main: Player) {
        val index = main.getNextMediaItemIndex()
        if (index == C.INDEX_UNSET) return
        val items = (0 until main.mediaItemCount).map(main::getMediaItemAt)
        second.setShuffleModeEnabled(main.shuffleModeEnabled)
        second.repeatMode = main.repeatMode
        second.volume = 0f
        second.setMediaItems(items, index, 0)
        second.prepare()
    }

    private fun createFading(main: ExoPlayer): ExoPlayer? {
        // Both players must share an audio session for the equalizer to cover the crossfade.
        val sessionId = main.audioSessionId
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return null
        // Without focus handling: were it to ask, the system would take the focus away from the
        // player still fading out, and the crossfade would pause itself. [flip] hands it over.
        val player = buildPlayer(handleFocus = false)
        player.setAudioSessionId(sessionId)
        player.addListener(playerListener(player))
        player.volume = 0f
        fading = player
        return player
    }

    private fun clearPreload() {
        fading?.let {
            it.stop()
            it.clearMediaItems()
        }
    }

    /** The item that would play after the current one, or null when there is no crossfade to be had. */
    private fun nextItem(main: Player): MediaItem? {
        val index = main.getNextMediaItemIndex()
        if (index == C.INDEX_UNSET || index == main.currentMediaItemIndex) return null
        return main.getMediaItemAt(index)
    }

    private fun replayGain(tracks: Tracks): Double? {
        val entries = tracks.groups.flatMap { group ->
            (0 until group.length).mapNotNull { group.getTrackFormat(it).metadata }
                .flatMap { metadata -> (0 until metadata.length()).map(metadata::get) }
        }
        val value = entries.firstNotNullOfOrNull { entry ->
            when (entry) {
                is TextInformationFrame -> entry.values.firstOrNull()
                    .takeIf { entry.description.equals(REPLAYGAIN_TRACK_GAIN, ignoreCase = true) }
                is VorbisComment -> entry.value.takeIf { entry.key.equals(REPLAYGAIN_TRACK_GAIN, ignoreCase = true) }
                is InternalFrame -> entry.text.takeIf { entry.description.equals(REPLAYGAIN_TRACK_GAIN, ignoreCase = true) }
                else -> null
            }
        } ?: return null
        return value.trim().removeSuffix("dB").removeSuffix("db").trim().toDoubleOrNull()
    }

    /** Puts a radio stream's ICY song title in the current item's artist, where every surface shows it. */
    private fun showStreamTitle(player: Player, metadata: Metadata) {
        val title = (0 until metadata.length()).map(metadata::get).filterIsInstance<IcyInfo>()
            .firstNotNullOfOrNull { it.title?.trim()?.takeIf(String::isNotEmpty) } ?: return
        val item = player.currentMediaItem?.takeIf { it.mediaId.startsWith(RADIO_PREFIX) } ?: return
        if (item.mediaMetadata.artist?.toString() == title) return
        val updated = item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setArtist(title).build()).build()
        player.replaceMediaItem(player.currentMediaItemIndex, updated)
    }

    /** Restores the stream URI that MediaController strips from items it sends to the session. */
    private object Callback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.mapTo(mutableListOf()) { it.resolved() })
    }

    private companion object {
        const val MIN_LISTEN_MS = 30_000L
        const val FADE_TICK_MS = 50L
        const val REPLAYGAIN_TRACK_GAIN = "REPLAYGAIN_TRACK_GAIN"
        const val PROGRESS_TICK_MS = 1_000L
        val MUSIC_ATTRIBUTES = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val WIDGET_EVENTS = intArrayOf(
            Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
            Player.EVENT_REPEAT_MODE_CHANGED,
            Player.EVENT_MEDIA_ITEM_TRANSITION,
            Player.EVENT_MEDIA_METADATA_CHANGED,
            Player.EVENT_IS_PLAYING_CHANGED,
            Player.EVENT_PLAY_WHEN_READY_CHANGED,
            Player.EVENT_PLAYBACK_STATE_CHANGED,
            Player.EVENT_TIMELINE_CHANGED,
        )
    }
}
