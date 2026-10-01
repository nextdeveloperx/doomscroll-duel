package com.doomscrollduel.domain.properties

import com.doomscrollduel.core.designsystem.brain.BrainState
import com.doomscrollduel.core.designsystem.brain.ReelUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Brain state, percent and HP checked for every combination a person could realistically reach. */
class BrainStatePropertyTest {

    @Test
    fun `every count and limit lands in the state its percent says`() {
        for (limit in 1..500) {
            for (used in 0..(limit * 3)) {
                val percent = ReelUsage.percentUsed(used, limit)
                val expected = when {
                    used * 100L >= limit * 100L -> BrainState.ZOMBIE // used >= limit
                    percent > 40 -> BrainState.FRIED
                    else -> BrainState.HAPPY
                }
                assertEquals("used=$used limit=$limit", expected, ReelUsage.brainState(used, limit))
            }
        }
    }

    @Test
    fun `watching one more reel never makes the brain healthier`() {
        for (limit in listOf(1, 7, 10, 33, 100, 250, 500)) {
            var previousState = BrainState.HAPPY
            var previousHp = 100
            for (used in 0..(limit * 2)) {
                val state = ReelUsage.brainState(used, limit)
                val hp = ReelUsage.hp(used, limit)
                assertTrue("state improved at used=$used limit=$limit", state.ordinal >= previousState.ordinal)
                assertTrue("hp rose at used=$used limit=$limit", hp <= previousHp)
                previousState = state
                previousHp = hp
            }
        }
    }

    @Test
    fun `hp is always between 0 and 100 and zero only at a full limit`() {
        for (limit in 1..300) {
            for (used in 0..(limit * 2)) {
                val hp = ReelUsage.hp(used, limit)
                assertTrue(hp in 0..100)
                if (hp == 0) assertTrue("hp 0 before the limit: used=$used limit=$limit", used >= limit)
                if (used >= limit) assertEquals(0, hp)
            }
        }
    }

    @Test
    fun `the first reel at exactly the limit is the first zombie and one before is not`() {
        for (limit in 1..500) {
            assertEquals(BrainState.ZOMBIE, ReelUsage.brainState(limit, limit))
            if (limit > 1) assertTrue(ReelUsage.brainState(limit - 1, limit) != BrainState.ZOMBIE)
        }
    }

    @Test
    fun `happy ends exactly at 40 percent of the limit`() {
        // limit 100: 40 reels is happy, 41 is fried.
        assertEquals(BrainState.HAPPY, ReelUsage.brainState(40, 100))
        assertEquals(BrainState.FRIED, ReelUsage.brainState(41, 100))
        // limit 10: 4 reels is 40 percent, 5 is 50.
        assertEquals(BrainState.HAPPY, ReelUsage.brainState(4, 10))
        assertEquals(BrainState.FRIED, ReelUsage.brainState(5, 10))
    }

    @Test
    fun `odd inputs never crash and never go out of range`() {
        for (limit in listOf(Int.MIN_VALUE, -5, 0, 1, Int.MAX_VALUE)) {
            for (used in listOf(Int.MIN_VALUE, -1, 0, 1, 1_000_000, Int.MAX_VALUE)) {
                val percent = ReelUsage.percentUsed(used, limit)
                assertTrue("percent $percent for used=$used limit=$limit", percent >= 0)
                assertTrue(ReelUsage.hp(used, limit) in 0..100)
                BrainState.fromPercentUsed(percent)
            }
        }
    }
}
