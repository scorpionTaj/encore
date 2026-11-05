package dev.scorpiontaj.encore.update

import androidx.media3.common.Player
import dev.scorpiontaj.encore.playback.nextRepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Comparing release versions and cycling the repeat mode, as the update flow and widget do. */
class UpdatesVersionTest {
    @Test
    fun `a higher release is newer`() {
        assertTrue(Updates.newer("1.10.0", "1.9.0"))
        assertTrue(Updates.newer("2.0.0", "1.9.9"))
        assertTrue(Updates.newer("1.9.1", "1.9.0"))
    }

    @Test
    fun `the same release and older ones are not`() {
        assertFalse(Updates.newer("1.9.0", "1.9.0"))
        assertFalse(Updates.newer("1.8.9", "1.9.0"))
    }

    @Test
    fun `suffixes are ignored`() {
        assertTrue(Updates.newer("1.9.0-arm64", "1.8.0"))
        assertFalse(Updates.newer("1.9.0-arm64", "1.9.0"))
    }

    @Test
    fun `versions that are not three numbers are never newer`() {
        assertFalse(Updates.newer("beta", "1.0.0"))
        assertFalse(Updates.newer("1.2", "1.0.0"))
        assertTrue(Updates.newer("1.2.3", "beta"))
    }

    @Test
    fun `repeat cycles off, all, one, off`() {
        assertEquals(Player.REPEAT_MODE_ALL, nextRepeatMode(Player.REPEAT_MODE_OFF))
        assertEquals(Player.REPEAT_MODE_ONE, nextRepeatMode(Player.REPEAT_MODE_ALL))
        assertEquals(Player.REPEAT_MODE_OFF, nextRepeatMode(Player.REPEAT_MODE_ONE))
    }
}
