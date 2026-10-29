package dev.scorpiontaj.encore.radio

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import dev.scorpiontaj.encore.BuildConfig
import dev.scorpiontaj.encore.library.EXTRA_MIME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/** Prefix of a radio station's media id; the rest is its Radio Browser uuid. */
const val RADIO_PREFIX = "radio:"

/** A Moroccan internet radio station from Radio Browser. */
data class Station(
    val uuid: String,
    val name: String,
    val url: String,
    val codec: String,
    /** Kilobits per second, 0 when unknown. */
    val bitrate: Int,
    val hls: Boolean,
    val tags: String,
) {
    fun toMediaItem(): MediaItem {
        val uri = url.toUri()
        val mime = if (hls) MimeTypes.APPLICATION_M3U8 else null
        return MediaItem.Builder()
            .setMediaId("$RADIO_PREFIX$uuid")
            .setUri(uri)
            .setMimeType(mime)
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder()
                    .setMediaUri(uri)
                    .setExtras(Bundle().apply { putString(EXTRA_MIME, mime) })
                    .build(),
            )
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setStation(name)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                    .build(),
            )
            .build()
    }
}

/** Reads stations from the Radio Browser directory. */
object RadioBrowser {
    private const val ENDPOINT = "https://all.api.radio-browser.info/json/stations/search" +
        "?countrycode=MA&hidebroken=true&order=clickcount&reverse=true&limit=300"
    private val CODECS = setOf("MP3", "AAC", "AAC+", "OGG")
    private const val TIMEOUT_MS = 15_000

    /** Moroccan stations by popularity, keeping only streams ExoPlayer plays: MP3, AAC, OGG and HLS. */
    suspend fun moroccanStations(): List<Station> = withContext(Dispatchers.IO) {
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        val body = try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "Encore-Android/${BuildConfig.VERSION_NAME}")
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "radio: HTTP ${connection.responseCode}" }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
        val stations = JSONArray(body)
        (0 until stations.length()).mapNotNull { index ->
            val json = stations.getJSONObject(index)
            val url = json.optString("url_resolved").ifBlank { json.optString("url") }.trim()
            val codec = json.optString("codec").trim().uppercase()
            val hls = json.optInt("hls") == 1 || url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
            val name = json.optString("name").trim()
            if (url.isEmpty() || name.isEmpty() || !(hls || codec in CODECS)) return@mapNotNull null
            Station(
                uuid = json.optString("stationuuid"),
                name = name,
                url = url,
                codec = if (hls) "HLS" else codec,
                bitrate = json.optInt("bitrate"),
                hls = hls,
                tags = json.optString("tags").split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(3)
                    .joinToString(", "),
            )
        }.distinctBy { it.url }
    }
}
