package app.rasoi

import app.rasoi.sos.TapCounter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapCounterTest {
    private val window = Config.TRIGGER_WINDOW_MS

    @Test fun fourTapsDoNotTrigger() {
        val c = TapCounter()
        repeat(4) { assertFalse(c.onTap("a", false, it * 100L).triggered) }
    }

    @Test fun fiveTapsInsideWindowTrigger() {
        val c = TapCounter()
        val results = (0 until 5).map { c.onTap("a", false, it * 1000L) }
        assertTrue(results.last().triggered)
        assertFalse(results.dropLast(1).any { it.triggered })
    }

    @Test fun fiveTapsAcrossRecipesDoNotTrigger() {
        val c = TapCounter()
        val ids = listOf("a", "a", "b", "b", "a")
        assertFalse(ids.mapIndexed { i, id -> c.onTap(id, false, i * 100L) }.any { it.triggered })
    }

    @Test fun fiveTapsOverTheWindowDoNotTrigger() {
        val c = TapCounter()
        val times = listOf(0L, 2_000, 4_000, 6_000, window + 1)
        assertFalse(times.map { c.onTap("a", false, it) }.any { it.triggered })
    }

    @Test fun pauseThenFreshSequenceTriggers() {
        val c = TapCounter()
        c.onTap("a", false, 0)
        c.onTap("a", false, 100)
        val start = window + 5_000
        val r = (0 until 5).map { c.onTap("a", false, start + it * 100L) }
        assertTrue(r.last().triggered)
    }

    @Test fun resetsAfterTriggerAndOnReset() {
        val c = TapCounter()
        repeat(5) { c.onTap("a", false, it * 10L) }
        assertFalse(c.onTap("a", false, 100).triggered)
        c.reset()
        repeat(3) { c.onTap("a", false, 200L + it) }
        c.reset()
        assertFalse(c.onTap("a", false, 300).triggered) // would be the 5th without the reset
    }

    /** Heart toggles on each normal tap; the 5th tap must put it back to what it was before tap 1. */
    @Test fun favouriteStateRestored() {
        for (startFav in listOf(false, true)) {
            val c = TapCounter()
            var fav = startFav
            var restored: Boolean? = null
            for (i in 0 until 5) {
                val r = c.onTap("a", fav, i * 100L)
                if (r.triggered) { fav = r.favouriteBeforeSequence; restored = fav } else fav = !fav
            }
            assertEquals(startFav, restored)
            assertEquals(startFav, fav)
        }
    }
}
