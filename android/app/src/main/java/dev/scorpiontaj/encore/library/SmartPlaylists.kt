package dev.scorpiontaj.encore.library

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** What a smart playlist rule looks at, as on the desktop. */
enum class RuleField { Title, Artist, Album, Genre, Format, Length, Added }

/** How a rule compares. Text fields use the first two; length and date the last three. */
enum class RuleOp { Contains, Is, AtLeast, AtMost, WithinDays }

/** One condition, such as "Artist contains Queen" or "Added within 30 days". */
data class Rule(val field: RuleField, val op: RuleOp, val value: String) {
    fun matches(song: Song, nowSeconds: Long): Boolean {
        val text = when (field) {
            RuleField.Title -> song.title
            RuleField.Artist -> song.artist
            RuleField.Album -> song.album
            RuleField.Genre -> song.genre.orEmpty()
            RuleField.Format -> song.format
            RuleField.Length, RuleField.Added -> ""
        }
        val number = value.trim().toDoubleOrNull()
        return when (op) {
            RuleOp.Contains -> value.isNotBlank() && text.contains(value.trim(), ignoreCase = true)
            RuleOp.Is -> text.equals(value.trim(), ignoreCase = true)
            // Length rules are in minutes.
            RuleOp.AtLeast -> number != null && field == RuleField.Length && song.durationMs >= number * 60_000
            RuleOp.AtMost -> number != null && field == RuleField.Length && song.durationMs <= number * 60_000
            RuleOp.WithinDays -> number != null && field == RuleField.Added && song.dateAdded >= nowSeconds - number * 86_400
        }
    }

    companion object {
        /** The comparisons that make sense for [field]. */
        fun opsFor(field: RuleField): List<RuleOp> = when (field) {
            RuleField.Length -> listOf(RuleOp.AtLeast, RuleOp.AtMost)
            RuleField.Added -> listOf(RuleOp.WithinDays)
            else -> listOf(RuleOp.Contains, RuleOp.Is)
        }
    }
}

/** A playlist whose songs are every library song matching its rules, kept current as the library changes. */
data class SmartPlaylist(val id: String, val name: String, val rules: List<Rule>, val matchAll: Boolean) {
    fun songs(library: Library): List<Song> {
        if (rules.isEmpty()) return emptyList()
        val now = System.currentTimeMillis() / 1000
        return library.songs.filter { song ->
            if (matchAll) rules.all { it.matches(song, now) } else rules.any { it.matches(song, now) }
        }
    }

    companion object {
        fun new(name: String, rules: List<Rule>, matchAll: Boolean) =
            SmartPlaylist(UUID.randomUUID().toString(), name, rules, matchAll)
    }
}

/** Smart playlists saved on the device as JSON in shared preferences. */
class SmartPlaylistStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("smart_playlists", Context.MODE_PRIVATE)

    fun load(): List<SmartPlaylist> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        List(array.length()) { index ->
            val json = array.getJSONObject(index)
            val rules = json.getJSONArray("rules")
            SmartPlaylist(
                id = json.getString("id"),
                name = json.getString("name"),
                matchAll = json.optBoolean("matchAll", true),
                rules = List(rules.length()) { r ->
                    val rule = rules.getJSONObject(r)
                    Rule(RuleField.valueOf(rule.getString("field")), RuleOp.valueOf(rule.getString("op")), rule.getString("value"))
                },
            )
        }
    }.getOrDefault(emptyList())

    fun save(playlists: List<SmartPlaylist>) {
        val array = JSONArray()
        playlists.forEach { playlist ->
            array.put(
                JSONObject()
                    .put("id", playlist.id)
                    .put("name", playlist.name)
                    .put("matchAll", playlist.matchAll)
                    .put(
                        "rules",
                        JSONArray().apply {
                            playlist.rules.forEach {
                                put(JSONObject().put("field", it.field.name).put("op", it.op.name).put("value", it.value))
                            }
                        },
                    ),
            )
        }
        prefs.edit { putString(KEY, array.toString()) }
    }

    private companion object {
        const val KEY = "playlists"
    }
}
