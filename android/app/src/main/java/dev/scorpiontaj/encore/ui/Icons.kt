package dev.scorpiontaj.encore.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.caverock.androidsvg.RenderOptions
import com.caverock.androidsvg.SVG
import androidx.core.graphics.createBitmap
import dev.scorpiontaj.encore.R

/** The desktop icon packs, by folder under assets/icons, with the name shown in Settings. */
val ICON_PACKS = listOf("lucide" to "Lucide", "remix" to "Remix", "iconoir" to "Iconoir", "solar" to "Solar")

/** The icon pack the app draws with, set from Settings at the top of the app. */
val LocalIconPack = compositionLocalOf { "lucide" }

/** Icons that point a way, flipped for right-to-left languages such as Arabic. */
private val MIRRORED = setOf("chevron-left", "chevron-right")

/** The app's icons by their old drawable, named as in the desktop packs. */
private val NAMES = mapOf(
    R.drawable.ic_play to "play-filled",
    R.drawable.ic_pause to "pause-filled",
    R.drawable.ic_skip_next to "skip-forward",
    R.drawable.ic_skip_previous to "skip-back",
    R.drawable.ic_shuffle to "shuffle",
    R.drawable.ic_repeat to "repeat",
    R.drawable.ic_repeat_one to "repeat-one",
    R.drawable.ic_home to "house",
    R.drawable.ic_audio_lines to "guitar",
    R.drawable.ic_music_note to "music",
    R.drawable.ic_album to "disc-3",
    R.drawable.ic_person to "user-round",
    R.drawable.ic_queue_music to "list-music",
    R.drawable.ic_radio to "radio",
    R.drawable.ic_expand_more to "chevron-down",
    R.drawable.ic_close to "x",
    R.drawable.ic_more_vert to "ellipsis",
    R.drawable.ic_arrow_back to "chevron-left",
    R.drawable.ic_favorite to "heart-filled",
    R.drawable.ic_favorite_border to "heart",
    R.drawable.ic_search to "search",
    R.drawable.ic_bar_chart to "chart-column",
    R.drawable.ic_tune to "sliders-horizontal",
    R.drawable.ic_settings to "settings",
    R.drawable.ic_lyrics to "mic-vocal",
    R.drawable.ic_info to "info",
    R.drawable.ic_sort to "arrow-up-down",
    R.drawable.ic_folder to "folder",
    R.drawable.ic_history to "rotate-ccw-clock",
    R.drawable.ic_bedtime to "moon",
    R.drawable.ic_record to "record",
    R.drawable.ic_stop to "record-stop",
    R.drawable.ic_add to "plus",
    R.drawable.ic_auto_awesome to "funnel",
    R.drawable.ic_delete to "trash-2",
)

/** An app icon from the chosen desktop pack, for a drawable the app used before the packs. */
@Composable
fun icon(drawable: Int): Painter = icon(NAMES[drawable] ?: "circle-alert")

/**
 * A desktop icon by name, from the chosen pack, then Lucide, then the common icons, as the
 * desktop falls back. It is drawn white so Material's Icon can tint it with the content colour.
 */
@Composable
fun icon(name: String): Painter {
    val context = LocalContext.current
    val pack = LocalIconPack.current
    val mirror = name in MIRRORED && LocalLayoutDirection.current == LayoutDirection.Rtl
    return remember(pack, name, mirror) {
        load(context, pack, name, mirror)?.let(::IconPainter) ?: ColorPainter(androidx.compose.ui.graphics.Color.Transparent)
    }
}

/**
 * Draws a rendered icon scaled to whatever size it is given. It reports no size of its own, so
 * Material's Icon gives it the standard 24 dp, as with any vector icon.
 */
private class IconPainter(private val bitmap: ImageBitmap) : Painter() {
    private var filter: ColorFilter? = null

    override val intrinsicSize: Size get() = Size.Unspecified

    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
        filter = colorFilter
        return true
    }

    override fun DrawScope.onDraw() {
        drawImage(
            bitmap,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            colorFilter = filter,
            filterQuality = FilterQuality.High,
        )
    }
}

private val rendered = LruCache<String, ImageBitmap>(256)

/** Size the SVG is drawn at; Compose scales it to the icon's size. */
private const val ICON_PX = 96

private fun load(context: Context, pack: String, name: String, mirror: Boolean): ImageBitmap? {
    val key = "$pack/$name/$mirror"
    rendered.get(key)?.let { return it }
    val svg = listOf(pack, "lucide", "common").firstNotNullOfOrNull { folder ->
        runCatching { SVG.getFromAsset(context.assets, "icons/$folder/$name.svg") }.getOrNull()
    } ?: return null
    svg.documentWidth = ICON_PX.toFloat()
    svg.documentHeight = ICON_PX.toFloat()
    val bitmap = createBitmap(ICON_PX, ICON_PX)
    val canvas = Canvas(bitmap)
    if (mirror) canvas.scale(-1f, 1f, ICON_PX / 2f, ICON_PX / 2f)
    svg.renderToCanvas(canvas, RenderOptions.create().css("svg { color: #ffffff }"))
    return bitmap.asImageBitmap().also { rendered.put(key, it) }
}
