package com.doomscrollduel.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveDuelTest {
    private fun outcome(mine: Int, theirs: Int) = ResolveDuel.forMe(mine, theirs)

    @Test
    fun `lower count wins when nobody reached the limit`() {
        assertEquals(DuelOutcome.WIN, outcome(mine = 31, theirs = 47))
        assertEquals(DuelOutcome.LOSS, outcome(mine = 47, theirs = 31))
    }

    @Test
    fun `reaching the limit loses even against a higher count`() {
        // 100 is the limit itself: ZOMBIE brain, stake forfeited.
        assertEquals(DuelOutcome.LOSS, outcome(mine = 100, theirs = 99))
        assertEquals(DuelOutcome.WIN, outcome(mine = 99, theirs = 100))
        assertEquals(DuelOutcome.LOSS, outcome(mine = 104, theirs = 38))
    }

    @Test
    fun `both at the limit falls back to the lower count`() {
        assertEquals(DuelOutcome.WIN, outcome(mine = 105, theirs = 140))
        assertEquals(DuelOutcome.LOSS, outcome(mine = 140, theirs = 105))
    }

    @Test
    fun `equal counts are a draw`() {
        assertEquals(DuelOutcome.DRAW, outcome(mine = 50, theirs = 50))
        assertEquals(DuelOutcome.DRAW, outcome(mine = 120, theirs = 120))
    }
}
