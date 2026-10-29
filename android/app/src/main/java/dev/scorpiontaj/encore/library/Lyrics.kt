package dev.scorpiontaj.encore.library

import android.net.Uri
import dev.scorpiontaj.encore.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** One lyric line, starting at [startMs]; unsynced lyrics have every line at -1. */
data class LyricLine(val startMs: Long, val text: String)

/** A song's lyrics. [synced] is true when every line carries a time to follow along with. */
data class Lyrics(val lines: List<LyricLine>, val synced: Boolean)

/** Looks songs up on LRCLIB, the lyrics source the desktop app asks first. */
object Lrclib {
    private const val BASE = "https://lrclib.net/api"
    private const val TIMEOUT_MS = 10_000
    private val STAMP = Regex("""\[(\d+):(\d+(?:\.\d+)?)]""")

    /** The song's lyrics, or null when LRCLIB has none or the song is instrumental. */
    suspend fun find(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        val exact = get(
            "$BASE/get?" + query(
                "track_name" to song.title,
                "artist_name" to song.artist,
                "album_name" to song.album,
                "duration" to (song.durationMs / 1000).toString(),
            ),
        )?.let(::JSONObject)
        val match = exact ?: get("$BASE/search?" + query("track_name" to song.title, "artist_name" to song.artist))
            ?.let(::JSONArray)?.takeIf { it.length() > 0 }?.getJSONObject(0)
        match?.let(::parse)
    }

    private fun parse(json: JSONObject): Lyrics? {
        if (json.optBoolean("instrumental")) return null
        val synced = json.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
        if (synced != null) {
            val lines = synced.lines().flatMap { line ->
                val text = line.replace(STAMP, "").trim()
                STAMP.findAll(line).map { stamp ->
                    val (minutes, seconds) = stamp.destructured
                    LyricLine(minutes.toLong() * 60_000 + (seconds.toDouble() * 1000).toLong(), text)
                }.toList()
            }.sortedBy { it.startMs }
            if (lines.isNotEmpty()) return Lyrics(lines, synced = true)
        }
        val plain = json.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" } ?: return null
        return Lyrics(plain.lines().map { LyricLine(-1, it.trim()) }, synced = false)
    }

    private fun get(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "Encore-Android/${BuildConfig.VERSION_NAME} (https://github.com/scorpionTaj/encore)")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun query(vararg pairs: Pair<String, String>): String =
        pairs.filter { it.second.isNotBlank() && it.second != UNKNOWN }
            .joinToString("&") { (key, value) -> "$key=${Uri.encode(value)}" }
}
