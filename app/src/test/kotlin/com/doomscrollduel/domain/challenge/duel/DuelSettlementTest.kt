package com.doomscrollduel.domain.challenge.duel

import com.doomscrollduel.domain.challenge.AMAN
import com.doomscrollduel.domain.challenge.ChallengeId
import com.doomscrollduel.domain.challenge.ChallengeMode
import com.doomscrollduel.domain.challenge.CoinOp
import com.doomscrollduel.domain.challenge.GapCause
import com.doomscrollduel.domain.challenge.ROHAN
import com.doomscrollduel.domain.challenge.T0
import com.doomscrollduel.domain.challenge.TrackingGap
import com.doomscrollduel.domain.challenge.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuelSettlementTest {
    private val start = T0
    private val end = T0.plus(DuelDuration.ONE_DAY.duration)
    private val duel = Duel(
        id = ChallengeId("d1"), mode = ChallengeMode.DUEL, creator = ROHAN, opponent = AMAN,
        config = DuelConfig(reelLimit = 100, duration = DuelDuration.ONE_DAY, stake = 50),
        state = DuelState.Active(start, end),
    )

    private fun rohan(reels: Int, vararg gaps: TrackingGap) = PlayerScore(ROHAN, reels, gaps.toList())
    private fun aman(reels: Int, vararg gaps: TrackingGap) = PlayerScore(AMAN, reels, gaps.toList())
    private fun settle(r: PlayerScore, a: PlayerScore) = DuelSettlement.settle(r, a, start, end)

    private fun off(startMin: Long, endMin: Long) =
        TrackingGap(start.plus(minutes(startMin)), start.plus(minutes(endMin)), GapCause.SERVICE_DISABLED)

    // ----- winner: lower count -------------------------------------------------------------------

    @Test
    fun `lower count wins`() {
        val result = settle(rohan(31), aman(47)) as DuelResult.Win
        assertEquals(ROHAN, result.winner)
        assertEquals(AMAN, result.loser)
        assertEquals(WinReason.LOWER_COUNT, result.reason)
        assertEquals(31, result.winnerReels)
        assertEquals(47, result.loserReels)
    }

    @Test
    fun `zero reels beats one reel`() {
        assertEquals(ROHAN, (settle(rohan(0), aman(1)) as DuelResult.Win).winner)
    }

    @Test
    fun `only one player over the limit loses because their count is higher`() {
        assertEquals(AMAN, (settle(rohan(100), aman(99)) as DuelResult.Win).winner)
        assertEquals(ROHAN, (settle(rohan(99), aman(100)) as DuelResult.Win).winner)
    }

    @Test
    fun `both over the limit, the lower count still wins`() {
        val result = settle(rohan(180), aman(140)) as DuelResult.Win
        assertEquals(AMAN, result.winner)
        assertEquals(WinReason.LOWER_COUNT, result.reason)
    }

    @Test
    fun `a huge count does not overflow or confuse the comparison`() {
        assertEquals(AMAN, (settle(rohan(Int.MAX_VALUE), aman(Int.MAX_VALUE - 1)) as DuelResult.Win).winner)
    }

    // ----- tie ------------------------------------------------------------------------------------

    @Test
    fun `equal counts are a tie`() {
        assertEquals(DuelResult.Tie(42), settle(rohan(42), aman(42)))
        assertEquals(DuelResult.Tie(0), settle(rohan(0), aman(0)))
    }

    @Test
    fun `a tie over the limit is still a tie`() {
        assertEquals(DuelResult.Tie(150), settle(rohan(150), aman(150)))
    }

    @Test
    fun `a tie returns both stakes and nobody gains`() {
        val ops = DuelSettlement.payoutOps(duel, DuelResult.Tie(42))
        assertEquals(2, ops.size)
        assertTrue(ops.all { it is CoinOp.Release && it.amount == 50 })
        assertEquals(setOf(ROHAN, AMAN), ops.map { (it as CoinOp.Release).player }.toSet())
    }

    // ----- forfeit: the 10 minute rule ---------------------------------------------------------

    @Test
    fun `counter off for exactly 10 minutes is not a forfeit`() {
        // Rohan has far fewer reels but Aman's gap is exactly 10 minutes, so the count decides.
        val result = settle(rohan(5), aman(60, off(100, 110))) as DuelResult.Win
        assertEquals(ROHAN, result.winner)
        assertEquals(WinReason.LOWER_COUNT, result.reason)
    }

    @Test
    fun `counter off for more than 10 minutes forfeits even with the lowest count`() {
        val result = settle(rohan(60), aman(0, off(100, 111))) as DuelResult.Win
        assertEquals(ROHAN, result.winner)
        assertEquals(AMAN, result.loser)
        assertEquals(WinReason.OPPONENT_FORFEIT, result.reason)
        assertEquals(60, result.winnerReels)
        assertEquals(0, result.loserReels)
    }

    @Test
    fun `the creator forfeiting makes the opponent the winner`() {
        val result = settle(rohan(0, off(0, 600)), aman(500)) as DuelResult.Win
        assertEquals(AMAN, result.winner)
        assertEquals(WinReason.OPPONENT_FORFEIT, result.reason)
    }

    @Test
    fun `a dead phone is not a forfeit`() {
        val dead = TrackingGap(start.plus(minutes(10)), start.plus(minutes(900)), GapCause.DEVICE_OFF_OR_UNKNOWN)
        val result = settle(rohan(3, dead), aman(9)) as DuelResult.Win
        assertEquals(ROHAN, result.winner)
        assertEquals(WinReason.LOWER_COUNT, result.reason)
    }

    @Test
    fun `when both forfeit the one who crossed first loses`() {
        val result = settle(rohan(10, off(50, 80)), aman(10, off(200, 230))) as DuelResult.Win
        assertEquals(AMAN, result.winner) // Rohan ran out of time first
        assertEquals(WinReason.OPPONENT_FORFEIT, result.reason)
    }

    @Test
    fun `both crossing at the same instant is a mutual forfeit and returns the stakes`() {
        val result = settle(rohan(10, off(50, 80)), aman(99, off(50, 90)))
        assertEquals(DuelResult.MutualForfeit, result)
        val ops = DuelSettlement.payoutOps(duel, result)
        assertTrue(ops.all { it is CoinOp.Release })
        assertEquals(2, ops.size)
    }

    @Test
    fun `isForfeit separates forfeits from played-out results`() {
        assertTrue(DuelSettlement.isForfeit(DuelResult.MutualForfeit))
        assertTrue(DuelSettlement.isForfeit(DuelResult.Win(ROHAN, AMAN, 0, 0, WinReason.OPPONENT_FORFEIT)))
        assertFalse(DuelSettlement.isForfeit(DuelResult.Win(ROHAN, AMAN, 0, 1, WinReason.LOWER_COUNT)))
        assertFalse(DuelSettlement.isForfeit(DuelResult.Tie(3)))
    }

    // ----- coins ----------------------------------------------------------------------------------

    @Test
    fun `the winner gets the loser's stake and their own back`() {
        val win = DuelResult.Win(ROHAN, AMAN, 31, 47, WinReason.LOWER_COUNT)
        val ops = DuelSettlement.payoutOps(duel, win)
        val transfer = ops.filterIsInstance<CoinOp.Transfer>().single()
        assertEquals(AMAN, transfer.from)
        assertEquals(ROHAN, transfer.to)
        assertEquals(50, transfer.amount)
        val release = ops.filterIsInstance<CoinOp.Release>().single()
        assertEquals(ROHAN, release.player)
    }

    @Test
    fun `payout ids are stable so a replay is harmless`() {
        val win = DuelResult.Win(ROHAN, AMAN, 31, 47, WinReason.LOWER_COUNT)
        assertEquals(
            DuelSettlement.payoutOps(duel, win).map { it.id },
            DuelSettlement.payoutOps(duel, win).map { it.id },
        )
        assertEquals(2, DuelSettlement.payoutOps(duel, win).map { it.id }.toSet().size)
    }

    @Test
    fun `a duel without a stake moves no coins`() {
        val dare = duel.copy(mode = ChallengeMode.FORFEIT_DARE, config = duel.config.copy(stake = 0))
        assertTrue(DuelSettlement.holdOps(dare).isEmpty())
        assertTrue(DuelSettlement.payoutOps(dare, DuelResult.Tie(1)).isEmpty())
    }
}
