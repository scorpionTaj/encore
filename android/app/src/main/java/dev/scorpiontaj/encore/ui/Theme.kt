package dev.scorpiontaj.encore.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.scorpiontaj.encore.R
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

@Composable
fun EncoreTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
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
                painterResource(R.drawable.ic_music_note),
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
                painterResource(R.drawable.ic_radio),
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
