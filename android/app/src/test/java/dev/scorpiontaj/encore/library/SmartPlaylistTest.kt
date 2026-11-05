package dev.scorpiontaj.encore.library

import dev.scorpiontaj.encore.library.RuleField
import dev.scorpiontaj.encore.library.RuleOp
import dev.scorpiontaj.encore.library.SmartPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** How smart playlist rules pick their songs. */
class SmartPlaylistTest {
    private val now = 1_800_000_000L

    @Test
    fun `text rules match without caring about case`() {
        val rule = Rule(RuleField.Artist, RuleOp.Contains, "queen")
        assertTrue(rule.matches(song(artist = "Queen"), now))
        assertFalse(rule.matches(song(artist = "The Beatles"), now))
        assertFalse(Rule(RuleField.Artist, RuleOp.Contains, " ").matches(song(artist = "Queen"), now))
    }

    @Test
    fun `is rules match the whole field only`() {
        val rule = Rule(RuleField.Title, RuleOp.Is, "bohemian rhapsody")
        assertTrue(rule.matches(song(title = "Bohemian Rhapsody"), now))
        assertFalse(rule.matches(song(title = "Bohemian Rhapsody (Remastered)"), now))
    }

    @Test
    fun `length rules read minutes`() {
        val atLeast = Rule(RuleField.Length, RuleOp.AtLeast, "3")
        val atMost = Rule(RuleField.Length, RuleOp.AtMost, "2")
        assertTrue(atLeast.matches(song(durationMs = 3 * 60_000L), now))
        assertFalse(atLeast.matches(song(durationMs = 2 * 60_000L), now))
        assertTrue(atMost.matches(song(durationMs = 120_000), now))
        // A length rule against a song without one does not match.
        assertFalse(Rule(RuleField.Length, RuleOp.AtLeast, "3").matches(song(durationMs = 0), now))
    }

    @Test
    fun `added rules read days back from now`() {
        val withinWeek = Rule(RuleField.Added, RuleOp.WithinDays, "7")
        assertTrue(withinWeek.matches(song(dateAdded = now - 6 * 86_400), now))
        assertFalse(withinWeek.matches(song(dateAdded = now - 8 * 86_400), now))
    }

    @Test
    fun `every rule must match when all is asked, and one is enough when any is`() {
        val library = Library(
            songs = listOf(song(title = "A", artist = "X"), song(title = "B", artist = "Y")),
            albums = emptyList(),
            artists = emptyList(),
            playlists = emptyList(),
        )
        val all = SmartPlaylist("1", "Both", listOf(Rule(RuleField.Title, RuleOp.Is, "A"), Rule(RuleField.Artist, RuleOp.Is, "X")), matchAll = true)
        val any = SmartPlaylist("2", "Either", listOf(Rule(RuleField.Title, RuleOp.Is, "A"), Rule(RuleField.Artist, RuleOp.Is, "Y")), matchAll = false)
        assertEquals(listOf("A"), all.songs(library).map { it.title })
        assertEquals(listOf("A", "B"), any.songs(library).map { it.title })
    }

    @Test
    fun `a playlist without rules holds nothing`() {
        val library = Library(songs = listOf(song(title = "A")), albums = emptyList(), artists = emptyList(), playlists = emptyList())
        assertTrue(SmartPlaylist("1", "Empty", emptyList(), matchAll = true).songs(library).isEmpty())
    }

    @Test
    fun `the operations offered fit the field`() {
        assertEquals(listOf(RuleOp.AtLeast, RuleOp.AtMost), Rule.opsFor(RuleField.Length))
        assertEquals(listOf(RuleOp.WithinDays), Rule.opsFor(RuleField.Added))
        assertEquals(listOf(RuleOp.Contains, RuleOp.Is), Rule.opsFor(RuleField.Genre))
    }

    /** A song with the fields the rule tests look at. */
    private fun song(
        title: String = "Song",
        artist: String = "Artist",
        durationMs: Long = 60_000,
        dateAdded: Long = 0,
    ) = Song(
        id = title.hashCode().toLong(),
        title = title,
        artist = artist,
        album = "Album",
        albumId = 1,
        albumArtist = "",
        durationMs = durationMs,
        track = 1,
        mime = "audio/mpeg",
        bitrate = null,
        sampleRate = null,
        bitDepth = null,
        year = null,
        genre = null,
        composer = null,
        path = "Music/$title.flac",
        dateAdded = dateAdded,
        dateModified = 0,
    )
}
