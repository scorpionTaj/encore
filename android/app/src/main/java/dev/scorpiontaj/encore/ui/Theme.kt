package dev.scorpiontaj.encore.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.scorpiontaj.encore.R
import dev.scorpiontaj.encore.settings.SettingsState
import dev.scorpiontaj.encore.settings.ThemeFile
import dev.scorpiontaj.encore.settings.ThemeMode
import dev.scorpiontaj.encore.settings.Typeface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Encore's brand violet, shared with the desktop app and the launcher icon. */
private val Accent = Color(0xFF5B3DF5)

private val Light = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5DEFF),
    onPrimaryContainer = Color(0xFF1A0063),
    secondaryContainer = Color(0xFFE5DEFF),
    onSecondaryContainer = Color(0xFF1A0063),
    background = Color(0xFFFBF8FF),
    surface = Color(0xFFFBF8FF),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFC8BFFF),
    onPrimary = Color(0xFF2D0A9E),
    primaryContainer = Accent,
    onPrimaryContainer = Color.White,
    secondaryContainer = Color(0xFF3B2A8F),
    onSecondaryContainer = Color(0xFFE5DEFF),
    background = Color(0xFF131218),
    surface = Color(0xFF131218),
)

/** Whether the app draws dark: from an installed theme's background when one is in use, else from Settings. */
@Composable
fun isDark(settings: SettingsState, custom: ThemeFile? = null): Boolean = when {
    custom != null -> !custom.isLight
    settings.theme == ThemeMode.System -> isSystemInDarkTheme()
    else -> settings.theme == ThemeMode.Dark
}

/**
 * Encore's violet scheme, the wallpaper's colours when asked for on Android 12 and newer, or an
 * installed theme's colours when one is picked.
 */
@Composable
fun EncoreTheme(settings: SettingsState, custom: ThemeFile?, content: @Composable () -> Unit) {
    val dark = isDark(settings, custom)
    val context = LocalContext.current
    val scheme = when {
        custom != null -> schemeOf(custom)
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, typography = typographyFor(settings.typeface)) {
        CompositionLocalProvider(
            LocalIconPack provides settings.iconPack,
            LocalReduceMotion provides settings.reduceMotion,
            content = content,
        )
    }
}

/** Whether to skip transitions and other movement, from Settings → Appearance → Motion. */
val LocalReduceMotion = compositionLocalOf { false }

/** The desktop's Inter faces, shipped from assets/fonts. */
private val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Material's type scale in Inter, or left as Material's own system font. */
private fun typographyFor(typeface: Typeface): Typography {
    val base = Typography()
    if (typeface == Typeface.System) return base
    fun TextStyle.inter() = copy(fontFamily = Inter)
    return base.copy(
        displayLarge = base.displayLarge.inter(), displayMedium = base.displayMedium.inter(), displaySmall = base.displaySmall.inter(),
        headlineLarge = base.headlineLarge.inter(), headlineMedium = base.headlineMedium.inter(), headlineSmall = base.headlineSmall.inter(),
        titleLarge = base.titleLarge.inter(), titleMedium = base.titleMedium.inter(), titleSmall = base.titleSmall.inter(),
        bodyLarge = base.bodyLarge.inter(), bodyMedium = base.bodyMedium.inter(), bodySmall = base.bodySmall.inter(),
        labelLarge = base.labelLarge.inter(), labelMedium = base.labelMedium.inter(), labelSmall = base.labelSmall.inter(),
    )
}

/** Maps the desktop theme's colour names onto Material's roles, keeping Material's own for any it lacks. */
private fun schemeOf(theme: ThemeFile): ColorScheme {
    val base = if (theme.isLight) Light else Dark
    fun color(vararg keys: String): Color? = keys.firstNotNullOfOrNull { theme.colors[it] }?.let { Color(it) }
    val background = color("background") ?: base.background
    val foreground = color("foreground") ?: base.onBackground
    val primary = color("primary", "progress_bar") ?: base.primary
    return base.copy(
        primary = primary,
        onPrimary = color("primary_foreground") ?: base.onPrimary,
        primaryContainer = color("secondary", "muted") ?: base.primaryContainer,
        onPrimaryContainer = foreground,
        secondaryContainer = color("muted", "secondary") ?: base.secondaryContainer,
        onSecondaryContainer = foreground,
        background = background,
        onBackground = foreground,
        surface = background,
        onSurface = foreground,
        surfaceVariant = color("muted") ?: base.surfaceVariant,
        onSurfaceVariant = color("muted_foreground") ?: base.onSurfaceVariant,
        surfaceContainer = color("sidebar", "popover") ?: base.surfaceContainer,
        surfaceContainerHigh = color("popover", "secondary") ?: base.surfaceContainerHigh,
        surfaceContainerLow = color("sidebar") ?: base.surfaceContainerLow,
        outline = color("border") ?: base.outline,
        outlineVariant = color("table_row_border", "border") ?: base.outlineVariant,
        error = color("danger") ?: base.error,
        onError = color("danger_foreground") ?: base.onError,
    )
}

/** Small colour swatches of a theme, for its card in Settings and the install preview. */
@Composable
fun ThemeSwatches(theme: ThemeFile, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(modifier) {
        listOf("background", "primary", "foreground", "muted", "danger").mapNotNull { theme.colors[it] }.forEach {
            Box(
                Modifier
                    .padding(end = 4.dp)
                    .size(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(it)),
            )
        }
    }
}

/** Thumbnails already read, keyed by content URI. A missing cover is cached as [NoArt]. */
private val thumbnails = LruCache<Uri, Any>(256)
private object NoArt

/**
 * A square cover for a library song, read from its embedded art through MediaStore, with a note
 * glyph when there is none. [bitmap] wins when given, for art the player already decoded.
 */
@Composable
fun Artwork(uri: Uri?, modifier: Modifier = Modifier, size: Dp = 48.dp, bitmap: ImageBitmap? = null) {
    val context = LocalContext.current
    val loaded by produceState(initialValue = uri?.let { thumbnails.get(it) as? ImageBitmap }, uri) {
        if (uri == null || thumbnails.get(uri) != null) return@produceState
        val image = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.loadThumbnail(uri, Size(THUMBNAIL_PX, THUMBNAIL_PX), null).asImageBitmap()
            }.getOrNull()
        }
        thumbnails.put(uri, image ?: NoArt)
        value = image
    }
    val shown = bitmap ?: loaded
    Box(
        modifier
            .let { if (size == Dp.Unspecified) it.fillMaxWidth().aspectRatio(1f) else it.size(size) }
            .clip(RoundedCornerShape(if (size == Dp.Unspecified || size > 96.dp) 20.dp else 8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        when (shown) {
            null -> Icon(
                icon(R.drawable.ic_music_note),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            else -> Image(shown, contentDescription = null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

private const val THUMBNAIL_PX = 320

/** Station logos already fetched, keyed by URL. A logo that would not load is cached as [NoArt]. */
private val logos = LruCache<String, Any>(128)

/** A station's logo fetched from [url], with the radio glyph while it loads or when it has none. */
@Composable
fun StationArtwork(url: String?, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val loaded by produceState(initialValue = url?.let { logos.get(it) as? ImageBitmap }, url) {
        if (url.isNullOrBlank() || logos.get(url) != null) return@produceState
        val image = withContext(Dispatchers.IO) { runCatching { fetchLogo(url) }.getOrNull() }
        logos.put(url, image ?: NoArt)
        value = image
    }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        when (val shown = loaded) {
            null -> Icon(
                icon(R.drawable.ic_radio),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            else -> Image(shown, contentDescription = null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

private fun fetchLogo(url: String): ImageBitmap? {
    val connection = URL(url).openConnection() as HttpURLConnection
    return try {
        connection.connectTimeout = LOGO_TIMEOUT_MS
        connection.readTimeout = LOGO_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
        val bytes = connection.inputStream.use { it.readBytes() }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= THUMBNAIL_PX) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?.asImageBitmap()
    } finally {
        connection.disconnect()
    }
}

private const val LOGO_TIMEOUT_MS = 8_000
