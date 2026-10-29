package dev.scorpiontaj.encore.library

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.scorpiontaj.encore.radio.RADIO_PREFIX

/** How far back the stats look, as on the desktop Stats page. */
enum class Period(val days: Int?) {
    Week(7),
    Month(30),
    Year(365),
    All(null),
    ;

    /** The first moment counted, in milliseconds since the epoch. */
    fun since(now: Long = System.currentTimeMillis()): Long = days?.let { now - it * DAY_MS } ?: 0

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

data class TopSong(val mediaId: String, val title: String, val artist: String?, val plays: Int)

data class TopArtist(val name: String, val mediaId: String, val plays: Int)

/** What one period adds up to: plays, time listened and the songs and artists played most. */
data class Summary(val plays: Int, val listenedMs: Long, val songs: List<TopSong>, val artists: List<TopArtist>)

/**
 * Every listen, kept in `history.db` and never trimmed. PlaybackService writes a row when a track
 * has been heard long enough to count; the Stats page reads it back. Blocking: call off the main
 * thread.
 */
class History(context: Context) : SQLiteOpenHelper(context.applicationContext, "history.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE listens (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                media_id TEXT NOT NULL,
                title TEXT NOT NULL,
                artist TEXT,
                played_at INTEGER NOT NULL,
                listened_ms INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX listens_played_at ON listens (played_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun record(mediaId: String, title: String, artist: String?, listenedMs: Long) {
        writableDatabase.insert(
            "listens",
            null,
            ContentValues().apply {
                put("media_id", mediaId)
                put("title", title)
                put("artist", artist)
                put("played_at", System.currentTimeMillis())
                put("listened_ms", listenedMs)
            },
        )
    }

    /** Totals over every listen, and the top songs and artists among library songs only. */
    fun summary(period: Period): Summary {
        val db = readableDatabase
        val since = arrayOf(period.since().toString())
        val (plays, listened) = db.rawQuery(
            "SELECT COUNT(*), COALESCE(SUM(listened_ms), 0) FROM listens WHERE played_at >= ?",
            since,
        ).use { c -> if (c.moveToFirst()) c.getInt(0) to c.getLong(1) else 0 to 0L }
        val notRadio = "played_at >= ? AND media_id NOT LIKE '$RADIO_PREFIX%'"
        val songs = db.rawQuery(
            "SELECT media_id, title, artist, COUNT(*) AS n FROM listens WHERE $notRadio " +
                "GROUP BY media_id ORDER BY n DESC, MAX(played_at) DESC LIMIT $TOP",
            since,
        ).use { c ->
            buildList { while (c.moveToNext()) add(TopSong(c.getString(0), c.getString(1), c.getString(2), c.getInt(3))) }
        }
        val artists = db.rawQuery(
            "SELECT artist, MAX(media_id), COUNT(*) AS n FROM listens WHERE $notRadio AND artist IS NOT NULL " +
                "AND artist != '$UNKNOWN' GROUP BY artist ORDER BY n DESC LIMIT $TOP",
            since,
        ).use { c ->
            buildList { while (c.moveToNext()) add(TopArtist(c.getString(0), c.getString(1), c.getInt(2))) }
        }
        return Summary(plays, listened, songs, artists)
    }

    private companion object {
        /** How many songs and artists each top list holds. */
        const val TOP = 10
    }
}
