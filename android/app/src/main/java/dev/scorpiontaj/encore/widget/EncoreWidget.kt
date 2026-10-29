package dev.scorpiontaj.encore.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.scorpiontaj.encore.MainActivity
import dev.scorpiontaj.encore.R
import dev.scorpiontaj.encore.library.UNKNOWN
import dev.scorpiontaj.encore.playback.PlaybackService

/**
 * The 5x1 home-screen player. PlaybackService pushes every track and play-state change through
 * [publish]; the buttons send broadcasts back here, which reach the player through a short-lived
 * MediaController.
 */
class EncoreWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, render(context, current))
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action !in COMMANDS) return super.onReceive(context, intent)
        val pending = goAsync()
        // A receiver's own context may not bind services; the application context may.
        val app = context.applicationContext
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            val controller = runCatching { future.get() }.getOrNull()
            when (action) {
                ACTION_PREVIOUS -> controller?.seekToPrevious()
                ACTION_PLAY_PAUSE -> controller?.let { Util.handlePlayPauseButtonAction(it) }
                ACTION_NEXT -> controller?.seekToNext()
            }
            // Holding the binding briefly lets the service promote itself to the foreground
            // before the controller lets go of it.
            Handler(Looper.getMainLooper()).postDelayed({
                controller?.release()
                pending.finish()
            }, RELEASE_DELAY_MS)
        }, ContextCompat.getMainExecutor(context))
    }

    /** What the widget shows: null while nothing is loaded in the player. */
    private data class Snapshot(val title: String, val artist: String?, val playing: Boolean, val art: Bitmap?)

    companion object {
        private const val ACTION_PREVIOUS = "dev.scorpiontaj.encore.widget.PREVIOUS"
        private const val ACTION_PLAY_PAUSE = "dev.scorpiontaj.encore.widget.PLAY_PAUSE"
        private const val ACTION_NEXT = "dev.scorpiontaj.encore.widget.NEXT"
        private val COMMANDS = setOf(ACTION_PREVIOUS, ACTION_PLAY_PAUSE, ACTION_NEXT)
        private const val RELEASE_DELAY_MS = 1_000L
        private const val ART_SIZE_PX = 192

        @Volatile private var current: Snapshot? = null
        private var artKey = 0
        private var art: Bitmap? = null

        /** Redraws every placed widget from [player], or as idle when the player is gone. Main thread. */
        @androidx.annotation.OptIn(UnstableApi::class)
        fun publish(context: Context, player: Player?) {
            val snapshot = player?.takeIf { it.mediaItemCount > 0 }?.let {
                val metadata = it.mediaMetadata
                Snapshot(
                    title = (metadata.title ?: metadata.station ?: context.getString(R.string.app_name)).toString(),
                    artist = metadata.artist?.toString()?.takeUnless { a -> a == UNKNOWN }
                        ?: metadata.station?.let { _ -> context.getString(R.string.radio_station) },
                    playing = !Util.shouldShowPlayButton(it),
                    art = artwork(metadata.artworkData),
                )
            }
            if (snapshot == current) return
            current = snapshot
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, EncoreWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, render(context, snapshot))
        }

        /** Decodes embedded cover art once per distinct image, scaled to fit a RemoteViews bitmap. */
        private fun artwork(data: ByteArray?): Bitmap? {
            val key = data?.contentHashCode() ?: 0
            if (key == artKey) return art
            artKey = key
            art = data?.let {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(it, 0, it.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= ART_SIZE_PX && bounds.outHeight / (sample * 2) >= ART_SIZE_PX) sample *= 2
                BitmapFactory.decodeByteArray(it, 0, it.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?.let { bitmap -> bitmap.scale(ART_SIZE_PX, ART_SIZE_PX) }
            }
            return art
        }

        private fun render(context: Context, snapshot: Snapshot?): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget).apply {
                setTextViewText(R.id.widget_title, snapshot?.title ?: context.getString(R.string.app_name))
                setTextViewText(R.id.widget_artist, snapshot?.artist ?: context.getString(R.string.widget_idle))
                when (val bitmap = snapshot?.art) {
                    null -> setImageViewResource(R.id.widget_art, R.drawable.widget_art_placeholder)
                    else -> setImageViewBitmap(R.id.widget_art, bitmap)
                }
                val playing = snapshot?.playing == true
                setImageViewResource(R.id.widget_play_pause, if (playing) R.drawable.ic_pause else R.drawable.ic_play)
                setContentDescription(
                    R.id.widget_play_pause,
                    context.getString(if (playing) R.string.action_pause else R.string.action_play),
                )
                val open = PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                setOnClickPendingIntent(R.id.widget_art, open)
                setOnClickPendingIntent(R.id.widget_info, open)
                // With nothing loaded there is nothing to control, so every button opens the app.
                setOnClickPendingIntent(R.id.widget_previous, snapshot?.let { command(context, ACTION_PREVIOUS) } ?: open)
                setOnClickPendingIntent(R.id.widget_play_pause, snapshot?.let { command(context, ACTION_PLAY_PAUSE) } ?: open)
                setOnClickPendingIntent(R.id.widget_next, snapshot?.let { command(context, ACTION_NEXT) } ?: open)
            }

        private fun command(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, EncoreWidget::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
