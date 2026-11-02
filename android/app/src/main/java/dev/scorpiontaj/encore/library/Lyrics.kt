package dev.scorpiontaj.encore.library

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Base64
import android.util.Log
import dev.scorpiontaj.encore.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.zip.Inflater
import kotlin.math.abs

/** One lyric line, starting at [startMs]; unsynced lyrics have every line at -1. */
data class LyricLine(val startMs: Long, val text: String)

/** A song's lyrics and where they came from. [synced] is true when every line carries a time. */
data class Lyrics(val lines: List<LyricLine>, val synced: Boolean, val source: String)

/** LRC text, the format of .lrc files and of most online sources. */
object Lrc {
    private val STAMP = Regex("""\[(\d+):(\d+(?:\.\d+)?)]""")

    /** Synced lines when the text carries times, else its plain lines, or null when empty. */
    fun parse(text: String, source: String): Lyrics? {
        val synced = text.lines().flatMap { line ->
            val words = line.replace(STAMP, "").trim()
            STAMP.findAll(line).map { stamp ->
                val (minutes, seconds) = stamp.destructured
                LyricLine(minutes.toLong() * 60_000 + (seconds.toDouble() * 1000).toLong(), words)
            }.toList()
        }.sortedBy { it.startMs }
        if (synced.isNotEmpty()) return Lyrics(synced, synced = true, source)
        val plain = text.lines().map { it.trim() }.dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() }
        return plain.takeIf { it.isNotEmpty() }?.let { lines -> Lyrics(lines.map { LyricLine(-1, it) }, synced = false, source) }
    }

    /** The lines as LRC; a line with no time is written without a stamp. */
    fun write(lines: List<LyricLine>): String = lines.joinToString("\n") { line ->
        if (line.startMs < 0) {
            line.text
        } else {
            val centis = line.startMs / 10
            String.format(Locale.ROOT, "[%02d:%02d.%02d]%s", centis / 6000, centis / 100 % 60, centis % 100, line.text)
        }
    }
}

/**
 * The .lrc file beside a song, as the desktop app reads and writes it. Android keeps apps out of
 * other folders, so this works inside the folder the person granted Encore from the lyrics
 * editor (usually Music), through the storage document provider.
 */
object LyricsFiles {
    const val SOURCE = "file"

    fun read(resolver: ContentResolver, tree: Uri?, song: Song): Lyrics? {
        val uri = documentUri(tree, song) ?: return null
        val text = runCatching { resolver.openInputStream(uri)?.use { it.bufferedReader().readText() } }.getOrNull()
        return text?.let { Lrc.parse(it, SOURCE) }
    }

    /** Writes the lines to the song's .lrc, making the file when it does not exist. False when [tree] does not hold the song. */
    fun write(resolver: ContentResolver, tree: Uri, song: Song, lines: List<LyricLine>): Boolean {
        val uri = documentUri(tree, song) ?: return false
        val text = Lrc.write(lines).toByteArray()
        val target = when (exists(resolver, uri)) {
            true -> uri
            false -> {
                val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getDocumentId(uri).substringBeforeLast('/'))
                DocumentsContract.createDocument(resolver, parent, "application/octet-stream", lrcName(song))
            }
        } ?: return false
        resolver.openOutputStream(target, "wt")?.use { it.write(text) } ?: return false
        return true
    }

    /** Whether the document is there. The provider answers a missing one with an exception, not an empty result. */
    private fun exists(resolver: ContentResolver, uri: Uri): Boolean = runCatching {
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { it.moveToFirst() } == true
    }.getOrDefault(false)

    /** True when [tree] holds the song's folder, so its .lrc can be read and written. */
    fun covers(tree: Uri?, song: Song): Boolean = documentUri(tree, song) != null

    private fun documentUri(tree: Uri?, song: Song): Uri? {
        if (tree == null || song.path.isEmpty()) return null
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return null
        val volume = treeId.substringBefore(':')
        val id = "$volume:${song.folder}${lrcName(song)}"
        val inside = treeId.endsWith(":") || id.startsWith("$treeId/")
        return if (inside) DocumentsContract.buildDocumentUriUsingTree(tree, id) else null
    }

    private fun lrcName(song: Song) = song.path.substringAfterLast('/').substringBeforeLast('.') + ".lrc"
}

/**
 * Lyrics from the web, asked in the desktop app's order: LRCLIB first, then NetEase, Kugou and
 * Musixmatch, keeping the first that has the song. None of them needs an account.
 */
object OnlineLyrics {
    private const val TIMEOUT_MS = 10_000
    private const val AGENT = "Encore-Android/${BuildConfig.VERSION_NAME} (https://github.com/scorpionTaj/encore)"

    /** How far apart, in seconds, a source's song may be from ours and still count as the same. */
    private const val SLACK_S = 5

    suspend fun find(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        val sources: List<Pair<String, (Song) -> Lyrics?>> = listOf(
            "LRCLIB" to ::lrclib,
            "NetEase" to ::netease,
            "Kugou" to ::kugou,
            "Musixmatch" to ::musixmatch,
        )
        sources.firstNotNullOfOrNull { (name, source) ->
            runCatching { source(song) }.onFailure { Log.w("lyrics", "lyrics: $name failed", it) }.getOrNull()
        }
    }

    private fun lrclib(song: Song): Lyrics? {
        val base = "https://lrclib.net/api"
        val exact = get(
            "$base/get?" + query("track_name" to song.title, "artist_name" to song.artist, "album_name" to song.album, "duration" to "${song.durationMs / 1000}"),
        )?.let(::JSONObject)
        val match = exact ?: get("$base/search?" + query("track_name" to song.title, "artist_name" to song.artist))
            ?.let(::JSONArray)?.takeIf { it.length() > 0 }?.getJSONObject(0) ?: return null
        if (match.optBoolean("instrumental")) return null
        return text(match, "syncedLyrics")?.let { Lrc.parse(it, "LRCLIB") }
            ?: text(match, "plainLyrics")?.let { Lrc.parse(it, "LRCLIB") }
    }

    private fun netease(song: Song): Lyrics? {
        val referer = "Referer" to "https://music.163.com"
        val search = get("https://music.163.com/api/search/get?" + query("s" to "${song.title} ${song.artist}", "type" to "1", "limit" to "5"), referer)
            ?.let(::JSONObject)?.optJSONObject("result")?.optJSONArray("songs") ?: return null
        val candidates = List(search.length(), search::getJSONObject)
            .filter { close(it.optLong("duration") / 1000, song) }
            .sortedBy { abs(it.optLong("duration") - song.durationMs) }
        for (candidate in candidates.take(2)) {
            val sheet = get(
                "https://music.163.com/api/song/lyric/v1?" + query(
                    "id" to candidate.getLong("id").toString(), "cp" to "false", "lv" to "0", "tv" to "0",
                    "rv" to "0", "kv" to "0", "yv" to "0", "ytv" to "0", "yrv" to "0",
                ),
                referer,
            )?.let(::JSONObject) ?: continue
            if (sheet.optBoolean("pureMusic")) return null
            sheet.optJSONObject("lrc")?.let { text(it, "lyric") }?.let { Lrc.parse(it, "NetEase") }?.let { return it }
        }
        return null
    }

    private fun kugou(song: Song): Lyrics? {
        val search = get(
            "https://mobiles.kugou.com/api/v3/search/song?" + query(
                "format" to "json", "keyword" to "${song.title} ${song.artist}", "page" to "1", "pagesize" to "20", "showtype" to "1",
            ),
        )?.let(::JSONObject)?.optJSONObject("data")?.optJSONArray("info") ?: return null
        val songs = List(search.length(), search::getJSONObject)
            .filter { close(it.optLong("duration"), song) }
            .sortedBy { abs(it.optLong("duration") * 1000 - song.durationMs) }
        for (found in songs.take(2)) {
            val candidates = get(
                "https://lyrics.kugou.com/search?" + query(
                    "ver" to "1", "man" to "yes", "client" to "mobi", "hash" to found.getString("hash"),
                    "duration" to (found.optLong("duration") * 1000).toString(),
                ),
            )?.let(::JSONObject)?.optJSONArray("candidates") ?: continue
            if (candidates.length() == 0) continue
            val candidate = candidates.getJSONObject(0)
            val content = get(
                "https://lyrics.kugou.com/download?" + query(
                    "ver" to "1", "client" to "pc", "id" to candidate.getString("id"), "accesskey" to candidate.getString("accesskey"),
                    "fmt" to "krc", "charset" to "utf8",
                ),
            )?.let(::JSONObject)?.let { text(it, "content") } ?: continue
            krc(decodeKrc(content))?.let { return it }
        }
        return null
    }

    /** Musixmatch's anonymous desktop-app token, fetched once per run. */
    @Volatile private var musixmatchToken: String? = null

    private fun musixmatch(song: Song): Lyrics? {
        val base = "https://apic-desktop.musixmatch.com/ws/1.1"
        val app = "web-desktop-app-v1.0"
        val browser = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        val headers = arrayOf("User-Agent" to browser, "Cookie" to "x-mxm-token-guid=")
        val token = musixmatchToken ?: get("$base/token.get?" + query("format" to "json", "app_id" to app), *headers)
            ?.let(::JSONObject)?.optJSONObject("message")?.optJSONObject("body")?.optString("user_token")
            ?.takeIf { it.isNotEmpty() && !it.contains("UpgradeOnly") }?.also { musixmatchToken = it } ?: return null
        val answer = get(
            "$base/macro.subtitles.get?" + query(
                "format" to "json", "namespace" to "lyrics_richsynched", "subtitle_format" to "mxm", "app_id" to app,
                "usertoken" to token, "q_track" to song.title, "q_artist" to song.artist, "q_album" to song.album,
                "q_duration" to "${song.durationMs / 1000}",
            ),
            *headers,
        )?.let(::JSONObject) ?: return null
        val body = answer.optJSONObject("message")?.optJSONObject("body")?.optJSONObject("macro_calls")
            ?.optJSONObject("track.subtitles.get")?.optJSONObject("message")?.optJSONObject("body")
            ?.optJSONArray("subtitle_list")?.optJSONObject(0)?.optJSONObject("subtitle")
            ?.let { text(it, "subtitle_body") } ?: return null
        val cues = runCatching { JSONArray(body) }.getOrNull() ?: return Lrc.parse(body, "Musixmatch")
        val lines = List(cues.length(), cues::getJSONObject).map { cue ->
            LyricLine((cue.optJSONObject("time")?.optDouble("total") ?: 0.0).times(1000).toLong(), cue.optString("text"))
        }
        return lines.takeIf { it.isNotEmpty() }?.let { Lyrics(it, synced = true, "Musixmatch") }
    }

    /** Kugou's KRC: base64, a 4-byte header, a fixed XOR mask, then zlib. */
    private fun decodeKrc(content: String): String {
        val packed = Base64.decode(content, Base64.DEFAULT).drop(4)
        val mask = byteArrayOf(0x40, 0x47, 0x61, 0x77, 0x5e, 0x32, 0x74, 0x47, 0x51, 0x36, 0x31, 0x2d, 0xce.toByte(), 0xd2.toByte(), 0x6e, 0x69)
        val unmasked = ByteArray(packed.size) { (packed[it].toInt() xor mask[it % mask.size].toInt()).toByte() }
        val inflater = Inflater().apply { setInput(unmasked) }
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (!inflater.finished()) {
            val read = inflater.inflate(buffer)
            if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            out.write(buffer, 0, read)
        }
        inflater.end()
        return out.toString(Charsets.UTF_8.name())
    }

    /** KRC lines are "[start,length]<offset,length,0>word…"; the words make the line. */
    private fun krc(text: String): Lyrics? {
        val header = Regex("""^\[(\d+),(\d+)](.*)$""")
        val lines = text.lines().mapNotNull { line ->
            val match = header.find(line.trim()) ?: return@mapNotNull null
            val words = match.groupValues[3].replace(Regex("<[^>]*>"), "").trim()
            words.takeIf { it.isNotEmpty() }?.let { LyricLine(match.groupValues[1].toLong(), it) }
        }
        return lines.takeIf { it.isNotEmpty() }?.let { Lyrics(it, synced = true, "Kugou") }
    }

    private fun close(seconds: Long, song: Song) = song.durationMs <= 0 || abs(seconds - song.durationMs / 1000) <= SLACK_S

    private fun text(json: JSONObject, key: String) = json.optString(key).takeIf { it.isNotBlank() && it != "null" }

    private fun get(url: String, vararg headers: Pair<String, String>): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", AGENT)
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
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
