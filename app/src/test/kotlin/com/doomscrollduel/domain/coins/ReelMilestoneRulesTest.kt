package com.doomscrollduel.domain.coins

import org.junit.Assert.assertEquals
import org.junit.Test

class ReelMilestoneRulesTest {

    @Test
    fun `milestone table from the spec`() {
        // reels -> cumulative coins
        val cases = mapOf(
            0 to 0,
            1 to 0,
            9 to 0,
            10 to 1,
            19 to 1,
            20 to 2,
            27 to 2,
            30 to 3,
            100 to 10,
            1000 to 100,
        )
        cases.forEach { (reels, coins) ->
            assertEquals("milestoneFor($reels)", coins, ReelMilestoneRules.milestoneFor(reels))
        }
    }

    @Test
    fun `remainder is preserved between milestones`() {
        assertEquals(0, ReelMilestoneRules.remainderFor(0))
        assertEquals(9, ReelMilestoneRules.remainderFor(9))
        assertEquals(0, ReelMilestoneRules.remainderFor(10))
        assertEquals(7, ReelMilestoneRules.remainderFor(27))
        assertEquals(0, ReelMilestoneRules.remainderFor(30))
    }

    @Test
    fun `newlyEligible is empty once a milestone is already credited`() {
        assertEquals(emptyList<Int>(), ReelMilestoneRules.newlyEligible(lastCredited = 1, totalReels = 15))
        assertEquals(emptyList<Int>(), ReelMilestoneRules.newlyEligible(lastCredited = 2, totalReels = 20))
        assertEquals(emptyList<Int>(), ReelMilestoneRules.newlyEligible(lastCredited = 0, totalReels = 9))
    }

    @Test
    fun `newlyEligible lists exactly the one new milestone for a normal single-reel advance`() {
        assertEquals(listOf(1), ReelMilestoneRules.newlyEligible(lastCredited = 0, totalReels = 10))
        assertEquals(listOf(2), ReelMilestoneRules.newlyEligible(lastCredited = 1, totalReels = 20))
        assertEquals(listOf(3), ReelMilestoneRules.newlyEligible(lastCredited = 2, totalReels = 30))
    }

    @Test
    fun `newlyEligible catches up every milestone crossed in one jump, in order`() {
        // 19 -> 45: milestone 1 already credited, 2/3/4 are all newly owed.
        assertEquals(listOf(2, 3, 4), ReelMilestoneRules.newlyEligible(lastCredited = 1, totalReels = 45))
    }

    @Test
    fun `newlyEligible never goes backwards for a stale or repeated total`() {
        // A repeated or older count update must not create extra rewards or reverse progress.
        assertEquals(emptyList<Int>(), ReelMilestoneRules.newlyEligible(lastCredited = 5, totalReels = 45)) // repeated 45
        assertEquals(emptyList<Int>(), ReelMilestoneRules.newlyEligible(lastCredited = 5, totalReels = 30)) // stale/older total
    }

    @Test
    fun `a negative total never produces a negative milestone or a crash`() {
        assertEquals(0, ReelMilestoneRules.milestoneFor(-5))
        assertEquals(0, ReelMilestoneRules.remainderFor(-5))
        assertEquals(emptyList<Int>(), ReelMilestoneRules.newlyEligible(lastCredited = 0, totalReels = -5))
    }
}
