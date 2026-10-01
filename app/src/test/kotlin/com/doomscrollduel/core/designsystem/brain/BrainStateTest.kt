package com.doomscrollduel.core.designsystem.brain

import org.junit.Assert.assertEquals
import org.junit.Test

class BrainStateTest {
    @Test
    fun `up to 40 percent is happy`() {
        assertEquals(BrainState.HAPPY, BrainState.fromPercentUsed(0))
        assertEquals(BrainState.HAPPY, BrainState.fromPercentUsed(40))
    }

    @Test
    fun `41 to 99 percent is fried`() {
        assertEquals(BrainState.FRIED, BrainState.fromPercentUsed(41))
        assertEquals(BrainState.FRIED, BrainState.fromPercentUsed(99))
    }

    @Test
    fun `100 percent or more is zombie`() {
        assertEquals(BrainState.ZOMBIE, BrainState.fromPercentUsed(100))
        assertEquals(BrainState.ZOMBIE, BrainState.fromPercentUsed(250))
    }

    @Test
    fun `percent used rounds down so only a full limit is zombie`() {
        assertEquals(40, ReelUsage.percentUsed(used = 81, limit = 200)) // 40.5
        assertEquals(99, ReelUsage.percentUsed(used = 999, limit = 1000)) // 99.9
        assertEquals(BrainState.HAPPY, ReelUsage.brainState(used = 81, limit = 200))
        assertEquals(BrainState.FRIED, ReelUsage.brainState(used = 999, limit = 1000))
        assertEquals(BrainState.ZOMBIE, ReelUsage.brainState(used = 100, limit = 100))
    }

    @Test
    fun `hp is 100 minus percent used and never negative`() {
        assertEquals(100, ReelUsage.hpForPercentUsed(0))
        assertEquals(28, ReelUsage.hpForPercentUsed(72))
        assertEquals(0, ReelUsage.hpForPercentUsed(100))
        assertEquals(0, ReelUsage.hpForPercentUsed(180))
        assertEquals(60, ReelUsage.hp(used = 40, limit = 100))
    }

    @Test
    fun `zero or negative limit never divides by zero`() {
        assertEquals(0, ReelUsage.percentUsed(used = 0, limit = 0))
        assertEquals(100, ReelUsage.percentUsed(used = 3, limit = 0))
        assertEquals(100, ReelUsage.percentUsed(used = 3, limit = -5))
    }

    @Test
    fun `huge counts do not overflow`() {
        assertEquals(BrainState.ZOMBIE, ReelUsage.brainState(used = Int.MAX_VALUE, limit = 1))
    }
}
