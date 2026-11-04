package dev.scorpiontaj.encore.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.scale
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import dev.scorpiontaj.encore.MainActivity
import dev.scorpiontaj.encore.R
import dev.scorpiontaj.encore.library.UNKNOWN
import dev.scorpiontaj.encore.playback.PlaybackService

/** The widget's button commands, sent to [PlaybackService] as service intents. */
const val WIDGET_PREVIOUS = "dev.scorpiontaj.encore.widget.PREVIOUS"
const val WIDGET_PLAY_PAUSE = "dev.scorpiontaj.encore.widget.PLAY_PAUSE"
const val WIDGET_NEXT = "dev.scorpiontaj.encore.widget.NEXT"
const val WIDGET_SHUFFLE = "dev.scorpiontaj.encore.widget.SHUFFLE"
const val WIDGET_REPEAT = "dev.scorpiontaj.encore.widget.REPEAT"

/**
 * The 5x1 home-screen player with shuffle and repeat on either side of the transport. PlaybackService pushes every track and play-state change through
 * [publish]; the buttons send service intents straight to [PlaybackService], which applies them
 * to its own player.
 */
class EncoreWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, render(context, current))
    }

    /** What the widget shows: null while nothing is loaded in the player. */
    private data class Snapshot(
        val title: String,
        val artist: String?,
        val playing: Boolean,
        val shuffle: Boolean,
        val repeatMode: Int,
        val art: Bitmap?,
    )

    companion object {
        private const val ART_SIZE_PX = 192

        private const val PROGRESS_MAX = 1000

        @Volatile private var current: Snapshot? = null

        /** How far through the song the bar is, out of [PROGRESS_MAX]; null hides it, as for live radio. */
        @Volatile private var progress: Int? = null
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
                    shuffle = it.shuffleModeEnabled,
                    repeatMode = it.repeatMode,
                    art = artwork(metadata.artworkData),
                )
            }
            if (snapshot == current) return publishProgress(context, player)
            current = snapshot
            progress = progressOf(player)
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, EncoreWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, render(context, snapshot))
        }

        /**
         * Moves only the progress bar, for PlaybackService's once-a-second tick while playing.
         * Sends nothing when the bar would not visibly move.
         */
        fun publishProgress(context: Context, player: Player?) {
            val value = progressOf(player)
            if (value == progress) return
            progress = value
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, EncoreWidget::class.java))
            if (ids.isEmpty()) return
            manager.partiallyUpdateAppWidget(ids, RemoteViews(context.packageName, R.layout.widget).apply { showProgress(value) })
        }

        private fun progressOf(player: Player?): Int? {
            val p = player?.takeIf { it.mediaItemCount > 0 && it.isCurrentMediaItemSeekable } ?: return null
            val duration = p.duration.takeIf { it > 0 } ?: return null
            return (p.currentPosition * PROGRESS_MAX / duration).toInt().coerceIn(0, PROGRESS_MAX)
        }

        private fun RemoteViews.showProgress(value: Int?) {
            setViewVisibility(R.id.widget_progress, if (value == null) View.INVISIBLE else View.VISIBLE)
            setProgressBar(R.id.widget_progress, PROGRESS_MAX, value ?: 0, false)
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
                showProgress(snapshot?.let { progress })
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
                val shuffle = snapshot?.shuffle == true
                val repeatMode = snapshot?.repeatMode ?: Player.REPEAT_MODE_OFF
                setInt(R.id.widget_shuffle, "setColorFilter", context.getColor(toggleColor(shuffle)))
                setContentDescription(
                    R.id.widget_shuffle,
                    context.getString(if (shuffle) R.string.shuffle_on else R.string.shuffle_off),
                )
                setImageViewResource(
                    R.id.widget_repeat,
                    if (repeatMode == Player.REPEAT_MODE_ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat,
                )
                setInt(R.id.widget_repeat, "setColorFilter", context.getColor(toggleColor(repeatMode != Player.REPEAT_MODE_OFF)))
                setContentDescription(
                    R.id.widget_repeat,
                    context.getString(
                        when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> R.string.repeat_one
                            Player.REPEAT_MODE_ALL -> R.string.repeat_all
                            else -> R.string.repeat_off
                        },
                    ),
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
                setOnClickPendingIntent(R.id.widget_previous, snapshot?.let { command(context, WIDGET_PREVIOUS) } ?: open)
                setOnClickPendingIntent(R.id.widget_play_pause, snapshot?.let { command(context, WIDGET_PLAY_PAUSE) } ?: open)
                setOnClickPendingIntent(R.id.widget_next, snapshot?.let { command(context, WIDGET_NEXT) } ?: open)
                setOnClickPendingIntent(R.id.widget_shuffle, snapshot?.let { command(context, WIDGET_SHUFFLE) } ?: open)
                setOnClickPendingIntent(R.id.widget_repeat, snapshot?.let { command(context, WIDGET_REPEAT) } ?: open)
            }

        private fun toggleColor(on: Boolean): Int = if (on) R.color.accent else R.color.widget_text_secondary

        /** Starts [PlaybackService] with [action]; the service applies it to its own player. */
        private fun command(context: Context, action: String): PendingIntent = PendingIntent.getService(
            context,
            action.hashCode(),
            Intent(context, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
