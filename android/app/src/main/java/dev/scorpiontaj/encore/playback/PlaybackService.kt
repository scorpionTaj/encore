package dev.scorpiontaj.encore.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.scorpiontaj.encore.MainActivity
import dev.scorpiontaj.encore.library.History
import dev.scorpiontaj.encore.library.resolved
import dev.scorpiontaj.encore.radio.RADIO_PREFIX
import dev.scorpiontaj.encore.widget.EncoreWidget
import java.util.concurrent.Executors

/**
 * Owns the one player. Media3 posts the media notification and lock-screen controls from the
 * session; the app and the widget reach the player through a MediaController. The service also
 * writes listening history, attaches the equalizer and shows a radio stream's current song.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var history: History
    private val writer = Executors.newSingleThreadExecutor()

    /** The item being listened to, and how long it has actually played. */
    private var listening: MediaItem? = null
    private var listenedMs = 0L
    private var playingSince = 0L

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        history = History(this)
        Sound.attach(this, player.audioSessionId)
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(*WIDGET_EVENTS)) EncoreWidget.publish(this@PlaybackService, player)
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) = Sound.attach(this@PlaybackService, audioSessionId)

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val now = SystemClock.elapsedRealtime()
                if (isPlaying) {
                    playingSince = now
                } else if (playingSince > 0) {
                    listenedMs += now - playingSince
                    playingSince = 0
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // A radio song change replaces the item in place; that is still the same listen.
                if (mediaItem != null && mediaItem.mediaId == listening?.mediaId) {
                    listening = mediaItem
                    return
                }
                commitListen(player.duration)
                listening = mediaItem
                listenedMs = 0
                playingSince = if (player.isPlaying) SystemClock.elapsedRealtime() else 0
            }

            override fun onMetadata(metadata: Metadata) = showStreamTitle(player, metadata)
        })
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.player?.let { commitListen(it.duration) }
        Sound.release()
        writer.shutdown()
        session?.run {
            player.release()
            release()
        }
        session = null
        EncoreWidget.publish(this, null)
        super.onDestroy()
    }

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
