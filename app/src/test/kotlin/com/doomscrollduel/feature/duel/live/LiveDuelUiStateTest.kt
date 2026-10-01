package com.doomscrollduel.feature.duel.live

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveDuelUiStateTest {
    private fun state(mine: Int, theirs: Int, limit: Int = 100) = LiveDuelUiState(
        me = LiveFighter("Rohan", mine),
        opponent = LiveFighter("Aman", theirs),
        reelLimit = limit,
        timeLeft = "01:00:00",
        stakeCoins = 50,
    )

    @Test
    fun `no warning while more than 5 reels are left`() {
        assertEquals(LiveWarning.None, state(mine = 94, theirs = 10).warning)
    }

    @Test
    fun `warns within 5 reels of the limit`() {
        assertEquals(LiveWarning.Close(5), state(mine = 95, theirs = 10).warning)
        assertEquals(LiveWarning.Close(4), state(mine = 96, theirs = 10).warning)
        assertEquals(LiveWarning.Close(1), state(mine = 99, theirs = 10).warning)
    }

    @Test
    fun `limit reached or passed`() {
        assertEquals(LiveWarning.LimitReached, state(mine = 100, theirs = 10).warning)
        assertEquals(LiveWarning.LimitReached, state(mine = 130, theirs = 10).warning)
    }

    @Test
    fun `lower count is ahead`() {
        assertEquals(Lead.Opponent(38), state(mine = 96, theirs = 58).lead)
        assertEquals(Lead.You(10), state(mine = 40, theirs = 50).lead)
        assertEquals(Lead.Tied, state(mine = 40, theirs = 40).lead)
    }
}
