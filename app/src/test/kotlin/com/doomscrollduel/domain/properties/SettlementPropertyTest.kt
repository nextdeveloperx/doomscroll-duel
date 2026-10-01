package com.doomscrollduel.domain.properties

import com.doomscrollduel.domain.challenge.ChallengeId
import com.doomscrollduel.domain.challenge.ChallengeMode
import com.doomscrollduel.domain.challenge.CoinLedger
import com.doomscrollduel.domain.challenge.ForfeitRule
import com.doomscrollduel.domain.challenge.GapCause
import com.doomscrollduel.domain.challenge.LedgerResult
import com.doomscrollduel.domain.challenge.PlayerId
import com.doomscrollduel.domain.challenge.TrackingGap
import com.doomscrollduel.domain.challenge.duel.Duel
import com.doomscrollduel.domain.challenge.duel.DuelConfig
import com.doomscrollduel.domain.challenge.duel.DuelDuration
import com.doomscrollduel.domain.challenge.duel.DuelResult
import com.doomscrollduel.domain.challenge.duel.DuelSettlement
import com.doomscrollduel.domain.challenge.duel.DuelState
import com.doomscrollduel.domain.challenge.duel.PlayerScore
import com.doomscrollduel.domain.challenge.duel.WinReason
import java.time.Duration
import java.time.Instant
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Random duels, checked against rules written independently of the settlement code. Seeds are fixed. */
class SettlementPropertyTest {
    private val start: Instant = Instant.parse("2026-10-01T10:00:00Z")
    private val end: Instant = start.plus(Duration.ofHours(24))
    private val creator = PlayerId("creator")
    private val opponent = PlayerId("opponent")

    private fun randomGaps(rnd: Random): List<TrackingGap> = List(rnd.nextInt(0, 4)) {
        val from = start.plusSeconds(rnd.nextLong(0, 24 * 3600))
        val len = rnd.nextLong(0, 40 * 60) // up to 40 minutes
        TrackingGap(from, from.plusSeconds(len), GapCause.SERVICE_DISABLED)
    }

    private fun score(p: PlayerId, rnd: Random) = PlayerScore(p, rnd.nextInt(0, 300), randomGaps(rnd))

    private fun duel(stake: Int) = Duel(
        id = ChallengeId("d"), mode = if (stake == 0) ChallengeMode.FORFEIT_DARE else ChallengeMode.DUEL, creator = creator, opponent = opponent,
        config = DuelConfig(reelLimit = 100, duration = DuelDuration.ONE_DAY, stake = stake),
        state = DuelState.Active(start, end),
    )

    private fun settle(a: PlayerScore, b: PlayerScore) = DuelSettlement.settle(a, b, start, end)

    @Test
    fun `swapping who created the duel never changes who wins`() {
        for (seed in 1..1500) {
            val rnd = Random(seed)
            val a = score(creator, rnd)
            val b = score(opponent, rnd)
            val r1 = settle(a, b)
            val r2 = settle(b, a)
            when (r1) {
                is DuelResult.Win -> {
                    r2 as DuelResult.Win
                    assertEquals("seed=$seed", r1.winner, r2.winner)
                    assertEquals(r1.reason, r2.reason)
                }
                is DuelResult.Tie -> assertTrue("seed=$seed", r2 is DuelResult.Tie)
                DuelResult.MutualForfeit -> assertEquals("seed=$seed", DuelResult.MutualForfeit, r2)
            }
        }
    }

    @Test
    fun `without forfeits the lower count wins and equal counts tie`() {
        for (seed in 1..1500) {
            val rnd = Random(seed)
            val a = PlayerScore(creator, rnd.nextInt(0, 200))
            val b = PlayerScore(opponent, rnd.nextInt(0, 200))
            val result = settle(a, b)
            when {
                a.reels < b.reels -> assertEquals(creator, (result as DuelResult.Win).winner)
                b.reels < a.reels -> assertEquals(opponent, (result as DuelResult.Win).winner)
                else -> assertEquals(DuelResult.Tie(a.reels), result)
            }
        }
    }

    @Test
    fun `one forfeit loses even with the best count, decided by the 10 minute rule alone`() {
        for (seed in 1..1500) {
            val rnd = Random(seed)
            val a = score(creator, rnd)
            val b = score(opponent, rnd)
            val aOut = ForfeitRule.forfeitMoment(a.gaps, start, end)
            val bOut = ForfeitRule.forfeitMoment(b.gaps, start, end)
            val result = settle(a, b)
            if (aOut != null && bOut == null) {
                result as DuelResult.Win
                assertEquals("seed=$seed", opponent, result.winner)
                assertEquals(WinReason.OPPONENT_FORFEIT, result.reason)
            }
            if (bOut != null && aOut == null) {
                result as DuelResult.Win
                assertEquals("seed=$seed", creator, result.winner)
                assertEquals(WinReason.OPPONENT_FORFEIT, result.reason)
            }
            if (aOut == null && bOut == null) assertTrue("seed=$seed", !DuelSettlement.isForfeit(result))
            if (aOut != null && bOut != null && aOut == bOut) assertEquals(DuelResult.MutualForfeit, result)
        }
    }

    @Test
    fun `payouts move exactly the stake and nothing else, and replaying them is harmless`() {
        for (seed in 1..800) {
            val rnd = Random(seed)
            val stake = listOf(1, 25, 50, 100, 1000).random(rnd)
            val d = duel(stake)
            val result = settle(score(creator, rnd), score(opponent, rnd))

            var ledger = CoinLedger(available = mapOf(creator to 2000, opponent to 2000))
            val total = ledger.total
            ledger = (ledger.applyAll(DuelSettlement.holdOps(d)) as LedgerResult.Applied).ledger
            val payout = DuelSettlement.payoutOps(d, result)
            ledger = (ledger.applyAll(payout) as LedgerResult.Applied).ledger

            assertEquals("coins created or destroyed, seed=$seed", total, ledger.total)
            assertEquals("escrow left over, seed=$seed", 0, ledger.heldOf(creator) + ledger.heldOf(opponent))
            when (result) {
                is DuelResult.Win -> {
                    assertEquals(2000 + stake, ledger.availableOf(result.winner))
                    assertEquals(2000 - stake, ledger.availableOf(result.loser))
                }
                else -> {
                    assertEquals(2000, ledger.availableOf(creator))
                    assertEquals(2000, ledger.availableOf(opponent))
                }
            }

            val again = ledger.applyAll(payout) as LedgerResult.Applied
            assertEquals("double settlement paid twice, seed=$seed", ledger.available, again.ledger.available)
            assertEquals(payout.size, again.skippedDuplicates)
            assertEquals(payout.size, payout.map { it.id }.toSet().size)
        }
    }

    @Test
    fun `a dare duel with no coin stake never moves a coin`() {
        for (seed in 1..200) {
            val rnd = Random(seed)
            val d = duel(0)
            val result = settle(score(creator, rnd), score(opponent, rnd))
            assertTrue(DuelSettlement.holdOps(d).isEmpty())
            assertTrue(DuelSettlement.payoutOps(d, result).isEmpty())
        }
    }

    @Test
    fun `a forfeit moment exists only when a gap really is longer than ten minutes`() {
        val short = listOf(TrackingGap(start, start.plus(Duration.ofMinutes(10)), GapCause.SERVICE_DISABLED))
        val long = listOf(TrackingGap(start, start.plus(Duration.ofMinutes(10)).plusSeconds(1), GapCause.SERVICE_DISABLED))
        assertNull(ForfeitRule.forfeitMoment(short, start, end))
        assertNotNull(ForfeitRule.forfeitMoment(long, start, end))
    }
}
