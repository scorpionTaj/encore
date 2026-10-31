package dev.scorpiontaj.encore.radio

import android.content.Context
import android.os.Bundle
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import dev.scorpiontaj.encore.BuildConfig
import dev.scorpiontaj.encore.library.EXTRA_MIME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/** Prefix of a radio station's media id; the rest is its Radio Browser uuid. */
const val RADIO_PREFIX = "radio:"

/** An internet radio station from Radio Browser. */
data class Station(
    val uuid: String,
    val name: String,
    val url: String,
    val codec: String,
    /** Kilobits per second, 0 when unknown. */
    val bitrate: Int,
    val hls: Boolean,
    val tags: String,
    /** A logo URL, empty when the station has none. */
    val favicon: String,
) {
    val mediaId: String get() = "$RADIO_PREFIX$uuid"

    fun toMediaItem(): MediaItem {
        val uri = url.toUri()
        val mime = if (hls) MimeTypes.APPLICATION_M3U8 else null
        return MediaItem.Builder()
            .setMediaId(mediaId)
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
                    .setArtworkUri(favicon.ifEmpty { null }?.toUri())
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                    .build(),
            )
            .build()
    }
}

/** The station behind a player item, rebuilt from what the item carries, or null for a song. */
fun stationOf(item: MediaItem?): Station? {
    val mediaId = item?.mediaId?.takeIf { it.startsWith(RADIO_PREFIX) } ?: return null
    val url = (item.requestMetadata.mediaUri ?: item.localConfiguration?.uri)?.toString() ?: return null
    val hls = item.requestMetadata.extras?.getString(EXTRA_MIME) == MimeTypes.APPLICATION_M3U8
    val name = (item.mediaMetadata.station ?: item.mediaMetadata.title)?.toString().orEmpty()
    return Station(mediaId.removePrefix(RADIO_PREFIX), name, url, if (hls) "HLS" else "", 0, hls, "", item.mediaMetadata.artworkUri?.toString().orEmpty())
}

/** Reads stations from the Radio Browser directory, the way the desktop Radio page does. */
object RadioBrowser {
    /** The countries the Radio tab offers, by ISO 3166 code, Morocco first. */
    val COUNTRIES = listOf("MA", "DZ", "TN", "LY", "MR", "EG", "SA", "AE", "QA", "LB", "FR", "BE", "ES", "GB", "US", "CA")
    const val HOME_COUNTRY = "MA"

    private const val STATIONS = "https://all.api.radio-browser.info/json/stations/search" +
        "?hidebroken=true&order=clickcount&reverse=true&limit=300&countrycode="

    /** Stations heard in Morocco that Radio Browser files under another country. */
    private const val ABROAD = "https://all.api.radio-browser.info/json/stations/byuuid?uuids=" +
        "c5c08ef9-5559-4073-87a8-80bff2b4860b,422c303d-2e17-11ea-aa0c-52543be04c81,71066c31-3c0f-47bf-ac4e-2ec0e417fba5"
    private val PREFIXES = listOf("MA:-", "MRC:-")

    /** Logos for stations whose Radio Browser entry has none or a broken one, by name fragment. */
    private val LOGOS = listOf(
        "radio mars" to "https://radiomars.ma/wp-content/uploads/2026/03/cropped-radio_mars-192x192.png",
        "med radio" to "https://medradio.ma/wp-content/uploads/2023/08/cropped-med-fav-jpg-192x192.jpg",
        "medi1" to "https://www.medi1.com/assets/favicon/apple-touch-icon.png",
        "medi 1" to "https://www.medi1.com/assets/favicon/apple-touch-icon.png",
        "hit radio" to "https://www.hitradio.ma/small-logo-hitradio.png",
        "ness radio" to "https://nessradio.com/wp-content/uploads/2026/09/cropped-ness-radio-favicon-192x192.png",
        "cap radio" to "https://capradiotv.com/wp-content/uploads/2023/11/cropped-LOGO-PNG-CAP-RADIO-192x192.png",
        "aswat" to "https://radioaswat.ma/wp-content/themes/aswata108/img/icons/touch.png",
        "adwaa" to "https://adwaafm.com/wp-content/uploads/2023/11/cropped-fgfg-1-180x180.png",
        "sawt al" to "https://radiolive.ma/logos/radio-sawt-alamal.png",
        "skyrock" to "https://skyrock.fm/apple-touch-icon.png",
    )
    private val CODECS = setOf("MP3", "AAC", "AAC+", "OGG")
    private val HLS_CODECS = setOf("MP3", "AAC", "AAC+")
    private const val TIMEOUT_MS = 15_000

    /** A country's working stations by popularity, keeping only streams ExoPlayer plays. */
    suspend fun stations(country: String): List<Station> = coroutineScope {
        val listed = async { fetch("$STATIONS$country") }
        val abroad = async {
            if (country == HOME_COUNTRY) runCatching { fetch(ABROAD) }.getOrDefault(emptyList()) else emptyList()
        }
        val all = listed.await() + abroad.await()
        val streams = mutableSetOf<String>()
        val names = mutableSetOf<String>()
        all.filter { streams.add(streamKey(it.url)) && names.add(it.name.lowercase()) }
    }

    private suspend fun fetch(url: String): List<Station> = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
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
            val stream = json.optString("url_resolved").ifBlank { json.optString("url") }.trim()
            val codec = json.optString("codec").trim().uppercase()
            val hls = json.optInt("hls") == 1
            val playable = when {
                !hls -> codec in CODECS
                stream.substringBefore('?').endsWith(".m3u8", ignoreCase = true) -> codec in HLS_CODECS
                else -> false
            }
            val name = PREFIXES.fold(json.optString("name").trim()) { n, prefix -> n.removePrefix(prefix) }
                .split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
            if (stream.isEmpty() || name.isEmpty() || !playable) return@mapNotNull null
            val lower = name.lowercase()
            Station(
                uuid = json.optString("stationuuid"),
                name = name,
                url = stream,
                codec = if (hls) "HLS" else codec,
                bitrate = json.optInt("bitrate"),
                hls = hls,
                tags = json.optString("tags").split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(3)
                    .joinToString(", "),
                favicon = LOGOS.firstOrNull { lower.contains(it.first) }?.second ?: json.optString("favicon").trim(),
            )
        }
    }

    /** A stream address without scheme, query or trailing punctuation, to spot one station listed twice. */
    private fun streamKey(url: String): String =
        url.trim().removePrefix("https://").removePrefix("http://").substringBefore('?').trimEnd('/', ';', '.').lowercase()
}

/** The Radio tab's remembered choices: the country shown and the favourite stations. */
class RadioPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("radio", Context.MODE_PRIVATE)

    var country: String
        get() = prefs.getString(KEY_COUNTRY, null)?.takeIf { it in RadioBrowser.COUNTRIES } ?: RadioBrowser.HOME_COUNTRY
        set(value) = prefs.edit { putString(KEY_COUNTRY, value) }

    var favourites: Set<String>
        get() = prefs.getStringSet(KEY_FAVOURITES, emptySet()).orEmpty().toSet()
        set(value) = prefs.edit { putStringSet(KEY_FAVOURITES, value) }

    private companion object {
        const val KEY_COUNTRY = "country"
        const val KEY_FAVOURITES = "favourites"
    }
}
