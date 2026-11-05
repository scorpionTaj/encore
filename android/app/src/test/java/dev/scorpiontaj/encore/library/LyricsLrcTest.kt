package dev.scorpiontaj.encore.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reading and writing LRC lyrics, the format of .lrc files. */
class LyricsLrcTest {
    @Test
    fun `stamped lines come back in time order with their text`() {
        val lyrics = Lrc.parse("[01:02.50]Second\n[00:10.00]First", "test")!!
        assertTrue(lyrics.synced)
        assertEquals(listOf(10_000L, 62_500L), lyrics.lines.map { it.startMs })
        assertEquals(listOf("First", "Second"), lyrics.lines.map { it.text })
    }

    @Test
    fun `one line may carry several stamps`() {
        val lyrics = Lrc.parse("[00:01.00][01:30.00]Chorus", "test")!!
        assertEquals(listOf(1_000L, 90_000L), lyrics.lines.map { it.startMs })
        assertEquals(listOf("Chorus", "Chorus"), lyrics.lines.map { it.text })
    }

    @Test
    fun `text without stamps stays unsynced, blank lines acting as verse separators`() {
        val lyrics = Lrc.parse("First\n\nSecond", "test")!!
        assertFalse(lyrics.synced)
        assertEquals(listOf("First", "", "Second"), lyrics.lines.map { it.text })
        assertTrue(lyrics.lines.all { it.startMs < 0 })
    }

    @Test
    fun `nothing to read is null`() {
        assertNull(Lrc.parse("", "test"))
        assertNull(Lrc.parse("   \n  ", "test"))
    }

    @Test
    fun `writing round-trips the times back through reading`() {
        val lines = listOf(LyricLine(0, "Start"), LyricLine(62_500, "Second"), LyricLine(-1, "Outro"))
        val lyrics = Lrc.parse(Lrc.write(lines), "test")!!
        assertTrue(lyrics.synced)
        assertEquals(listOf(0L, 62_500L), lyrics.lines.map { it.startMs })
        assertEquals(listOf("Start", "Second"), lyrics.lines.map { it.text })
    }
}
