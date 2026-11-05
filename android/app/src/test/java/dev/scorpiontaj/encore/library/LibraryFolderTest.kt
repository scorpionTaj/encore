package dev.scorpiontaj.encore.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The folder paths songs carry and the hidden-folder matching the library filters with. */
class LibraryFolderTest {
    @Test
    fun `a folder path keeps one trailing slash`() {
        assertEquals("Music/Album/", song(path = "Music/Album/01 Song.flac").folder)
        assertEquals("", song(path = "Song.flac").folder)
        assertEquals("Music/", song(path = "Music/Song.flac").folder)
    }

    @Test
    fun `a hidden folder covers itself and its subfolders`() {
        assertTrue(Library.isUnder("Music/Voice/", "Music/Voice/"))
        assertTrue(Library.isUnder("Music/Voice notes/2024/", "Music/Voice notes/"))
        assertFalse(Library.isUnder("Music/", "Music/Voice/"))
    }

    @Test
    fun `a half segment never hides a folder it only shares letters with`() {
        assertFalse(Library.isUnder("Music/Voicework/", "Music/Voice/"))
        assertFalse(Library.isUnder("Music/Voice/", "Music/V"))
    }

    @Test
    fun `an empty or slash-only entry hides nothing`() {
        assertFalse(Library.isUnder("Music/", ""))
        assertFalse(Library.isUnder("Music/", "/"))
        assertFalse(Library.isUnder("Music/", "//"))
    }

    @Test
    fun `entries are matched without caring about the edge slashes`() {
        assertTrue(Library.isUnder("Music/Voice/", "Music/Voice"))
        assertTrue(Library.isUnder("Music/Voice/", "/Music/Voice//"))
    }

    private fun song(path: String) = song(id = 1, path = path)

    /** A song with only the fields the folder tests look at. */
    private fun song(
        id: Long,
        path: String,
    ) = Song(
        id = id,
        title = "Song",
        artist = "Artist",
        album = "Album",
        albumId = 1,
        albumArtist = "",
        durationMs = 60_000,
        track = 1,
        mime = "audio/mpeg",
        bitrate = null,
        sampleRate = null,
        bitDepth = null,
        year = null,
        genre = null,
        composer = null,
        path = path,
        dateAdded = 0,
        dateModified = 0,
    )
}
