package dev.scorpiontaj.encore.settings

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * A theme in the desktop app's format: a name, an author, format version 1 and a `theme` map of
 * colour names to #rrggbb or #rrggbbaa. Android reads the colours it has a place for.
 */
data class ThemeFile(
    val id: String,
    val name: String,
    val author: String,
    val colors: Map<String, Long>,
    /** Shipped with the app, so it cannot be deleted. */
    val bundled: Boolean,
) {
    /** Light when the background is, as the desktop decides. */
    val isLight: Boolean get() = colors["background"]?.let(::luminance)?.let { it >= 0.5 } ?: false

    fun toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("author", author)
        .put("version", FORMAT_VERSION)
        .put("theme", JSONObject().apply { colors.forEach { (key, value) -> put(key, hex(value)) } })

    companion object {
        const val FORMAT_VERSION = 1
    }
}

/**
 * The themes Encore offers: the desktop's bundled ones, shipped in the APK, and any installed
 * from an encore://theme/ or sonora://theme/ link. Installed themes live in shared preferences.
 */
object Themes {
    private val SCHEMES = setOf("encore", "sonora")
    private const val LABEL_LIMIT = 64
    private const val LINK_LIMIT = 16 * 1024
    private const val PREFS = "themes"
    private const val KEY = "installed"

    fun all(context: Context): List<ThemeFile> = bundled(context) + installed(context)

    fun bundled(context: Context): List<ThemeFile> {
        val assets = context.assets
        fun walk(path: String): List<String> {
            val children = assets.list(path).orEmpty()
            return if (children.isEmpty()) listOf(path) else children.flatMap { walk("$path/$it") }
        }
        return walk("themes").filter { it.endsWith(".json") }.mapNotNull { path ->
            val id = path.removePrefix("themes/").removeSuffix(".json")
            runCatching { parse(assets.open(path).use { it.readBytes() }, id, bundled = true) }.getOrNull()
        }.sortedBy { it.name.lowercase() }
    }

    fun installed(context: Context): List<ThemeFile> = runCatching {
        val array = JSONArray(prefs(context).getString(KEY, "[]"))
        List(array.length()) { index ->
            val json = array.getJSONObject(index)
            parse(json.getJSONObject("file").toString().toByteArray(), json.getString("id"), bundled = false)
        }
    }.getOrDefault(emptyList())

    /** Adds [theme], replacing an installed one with the same id. */
    fun install(context: Context, theme: ThemeFile) {
        val kept = installed(context).filterNot { it.id == theme.id } + theme.copy(bundled = false)
        save(context, kept)
    }

    fun delete(context: Context, id: String) = save(context, installed(context).filterNot { it.id == id })

    /** The theme an encore://theme/<base64url JSON> or sonora://theme/… link carries, or null when it is not one. */
    fun fromLink(uri: Uri): ThemeFile? {
        if (uri.scheme?.lowercase() !in SCHEMES || uri.host != "theme") return null
        val payload = uri.encodedPath.orEmpty().trim('/').trimEnd('=')
        if (payload.isEmpty() || payload.length > LINK_LIMIT || !payload.all { it.isLetterOrDigit() || it == '-' || it == '_' }) return null
        val bytes = runCatching { Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP) }.getOrNull() ?: return null
        return runCatching { parse(bytes, null, bundled = false) }.getOrNull()
    }

    /** Reads a theme file. [id] is kept for known themes; a new one gets an id from its name and author. */
    private fun parse(bytes: ByteArray, id: String?, bundled: Boolean): ThemeFile {
        val json = JSONObject(String(bytes))
        require(json.getInt("version") == ThemeFile.FORMAT_VERSION) { "themes: unsupported theme version" }
        val name = json.getString("name").trim()
        val author = json.optString("author").trim()
        require(name.isNotEmpty() && name.length <= LABEL_LIMIT && author.length <= LABEL_LIMIT) { "themes: bad name or author" }
        require((name + author).none { Character.isISOControl(it) }) { "themes: control characters" }
        val map = json.getJSONObject("theme")
        val colors = map.keys().asSequence().mapNotNull { key -> parseColor(map.optString(key))?.let { key to it } }.toMap()
        require(colors.containsKey("background") && colors.containsKey("foreground")) { "themes: no background or foreground" }
        return ThemeFile(id ?: slug("$author-$name"), name, author, colors, bundled)
    }

    private fun save(context: Context, themes: List<ThemeFile>) {
        val array = JSONArray()
        themes.forEach { array.put(JSONObject().put("id", it.id).put("file", it.toJson())) }
        prefs(context).edit { putString(KEY, array.toString()) }
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun slug(text: String) = "user/" + text.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40)
}

/** #rrggbb or #rrggbbaa as an ARGB long, or null when it is neither. */
fun parseColor(value: String): Long? {
    val hex = value.trim().removePrefix("#")
    val parsed = hex.toLongOrNull(16) ?: return null
    return when (hex.length) {
        6 -> 0xFF000000 or parsed
        8 -> ((parsed and 0xFF) shl 24) or (parsed ushr 8)
        else -> null
    }
}

private fun hex(argb: Long): String {
    val alpha = (argb ushr 24) and 0xFF
    val rgb = argb and 0xFFFFFF
    return if (alpha == 0xFFL) "#%06x".format(rgb) else "#%06x%02x".format(rgb, alpha)
}

/** Relative lightness from 0 to 1, close enough to the desktop's HSL test for light and dark. */
private fun luminance(argb: Long): Double {
    val r = (argb shr 16 and 0xFF) / 255.0
    val g = (argb shr 8 and 0xFF) / 255.0
    val b = (argb and 0xFF) / 255.0
    return (maxOf(r, g, b) + minOf(r, g, b)) / 2
}
