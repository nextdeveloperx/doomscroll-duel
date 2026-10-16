package com.doomscrollduel.domain.coins

import org.junit.Assert.assertEquals
import org.junit.Test

class ReelMilestoneCoinsTest {
    @Test fun `odd milestones pay 1 coin and even ones pay 2`() {
        assertEquals(1, ReelMilestoneRules.coinsFor(1))
        assertEquals(2, ReelMilestoneRules.coinsFor(2))
        assertEquals(1, ReelMilestoneRules.coinsFor(3))
        assertEquals(2, ReelMilestoneRules.coinsFor(4))
    }

    @Test fun `10 reels average 1 and a half coins`() {
        // 10 reels 1, 20 reels 3, 30 reels 4, 40 reels 6, 100 reels 15
        assertEquals(listOf(0, 1, 3, 4, 6, 7, 9), (0..6).map { ReelMilestoneRules.totalCoinsFor(it) })
        assertEquals(15, ReelMilestoneRules.totalCoinsFor(ReelMilestoneRules.milestoneFor(100)))
        assertEquals(150, ReelMilestoneRules.totalCoinsFor(ReelMilestoneRules.milestoneFor(1000)))
    }

    @Test fun `the total is the sum of what each milestone pays`() {
        (0..200).forEach { n ->
            assertEquals("after $n milestones", (1..n).sumOf { ReelMilestoneRules.coinsFor(it) }, ReelMilestoneRules.totalCoinsFor(n))
        }
    }

    @Test fun `a negative count pays nothing`() {
        assertEquals(0, ReelMilestoneRules.totalCoinsFor(-5))
    }
}
