package com.doomscrollduel.domain.challenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForfeitRuleTest {
    private val from = T0
    private val to = T0.plus(hours(24))

    private fun off(startMin: Long, endMin: Long?, cause: GapCause = GapCause.SERVICE_DISABLED) =
        TrackingGap(T0.plus(minutes(startMin)), endMin?.let { T0.plus(minutes(it)) }, cause)

    @Test
    fun `exactly 10 minutes is fine, one second more forfeits`() {
        assertNull(ForfeitRule.forfeitMoment(listOf(off(60, 70)), from, to))
        val gap = TrackingGap(T0.plus(minutes(60)), T0.plus(minutes(70)).plusSeconds(1), GapCause.SERVICE_DISABLED)
        assertEquals(T0.plus(minutes(70)), ForfeitRule.forfeitMoment(listOf(gap), from, to))
    }

    @Test
    fun `the forfeit moment is when the 10 minutes ran out`() {
        assertEquals(T0.plus(minutes(70)), ForfeitRule.forfeitMoment(listOf(off(60, 200)), from, to))
    }

    @Test
    fun `only a switched off service counts, not a dead phone`() {
        val gap = off(60, 600, GapCause.DEVICE_OFF_OR_UNKNOWN)
        assertNull(ForfeitRule.forfeitMoment(listOf(gap), from, to))
    }

    @Test
    fun `several short gaps never add up`() {
        val gaps = listOf(off(10, 19), off(30, 39), off(50, 59), off(70, 79))
        assertNull(ForfeitRule.forfeitMoment(gaps, from, to))
    }

    @Test
    fun `touching or overlapping gaps are one long gap`() {
        // 0-6 then 6-12 is a continuous 12 minutes off.
        assertEquals(T0.plus(minutes(10)), ForfeitRule.forfeitMoment(listOf(off(0, 6), off(6, 12)), from, to))
        // 100-108 and 104-115 overlap into one span 100-115, so the 10 minutes ran out at minute 110.
        assertEquals(T0.plus(minutes(110)), ForfeitRule.forfeitMoment(listOf(off(100, 108), off(104, 115)), from, to))
    }

    @Test
    fun `a gap still open counts up to the end of the window`() {
        val openGap = off(24 * 60 - 30, null)
        assertEquals(T0.plus(minutes(24 * 60 - 20)), ForfeitRule.forfeitMoment(listOf(openGap), from, to))
        // Only 9 minutes left in the window: not enough.
        assertNull(ForfeitRule.forfeitMoment(listOf(off(24 * 60 - 9, null)), from, to))
    }

    @Test
    fun `time outside the duel window is ignored`() {
        // Off for an hour before the duel started, only 5 minutes of it inside.
        assertNull(ForfeitRule.forfeitMoment(listOf(off(-55, 5)), from, to))
        // Off from 20 minutes before the end to well after it: clipped to 20.
        assertEquals(T0.plus(minutes(24 * 60 - 10)), ForfeitRule.forfeitMoment(listOf(off(24 * 60 - 20, 24 * 60 + 500)), from, to))
    }

    @Test
    fun `the earliest of several long gaps is reported`() {
        val gaps = listOf(off(500, 560), off(100, 130))
        assertEquals(T0.plus(minutes(110)), ForfeitRule.forfeitMoment(gaps, from, to))
    }

    @Test
    fun `an empty or backwards window never forfeits`() {
        assertNull(ForfeitRule.forfeitMoment(listOf(off(0, 100)), to, from))
        assertNull(ForfeitRule.forfeitMoment(emptyList(), from, to))
    }
}

class CoinLedgerTest {
    private val start = CoinLedger(available = mapOf(ROHAN to 100, AMAN to 100))

    private fun applied(r: LedgerResult) = (r as LedgerResult.Applied).ledger

    @Test
    fun `hold moves coins to escrow and refuses an overdraft`() {
        val held = applied(start.applyAll(listOf(CoinOp.Hold("h1", ROHAN, 40))))
        assertEquals(60, held.availableOf(ROHAN))
        assertEquals(40, held.heldOf(ROHAN))
        val failed = start.applyAll(listOf(CoinOp.Hold("h2", ROHAN, 101))) as LedgerResult.Failed
        assertEquals(LedgerError.INSUFFICIENT_AVAILABLE, failed.error)
    }

    @Test
    fun `transfer pays from the losers escrow to the winner and release returns the rest`() {
        val held = applied(start.applyAll(listOf(CoinOp.Hold("h1", ROHAN, 50), CoinOp.Hold("h2", AMAN, 50))))
        val settled = applied(
            held.applyAll(
                listOf(CoinOp.Transfer("t", from = ROHAN, to = AMAN, amount = 50), CoinOp.Release("r", AMAN, 50)),
            ),
        )
        assertEquals(50, settled.availableOf(ROHAN))
        assertEquals(150, settled.availableOf(AMAN))
        assertEquals(0, settled.heldOf(ROHAN))
        assertEquals(0, settled.heldOf(AMAN))
    }

    @Test
    fun `coins are never created or destroyed`() {
        val ops = listOf(
            CoinOp.Hold("h1", ROHAN, 30), CoinOp.Hold("h2", AMAN, 30),
            CoinOp.Transfer("t", ROHAN, AMAN, 30), CoinOp.Release("r", AMAN, 30),
        )
        assertEquals(start.total, applied(start.applyAll(ops)).total)
    }

    @Test
    fun `the same op id applied twice changes nothing the second time`() {
        val once = applied(start.applyAll(listOf(CoinOp.Hold("h1", ROHAN, 40))))
        val twice = start.applyAll(listOf(CoinOp.Hold("h1", ROHAN, 40))).let { applied(it) }
            .applyAll(listOf(CoinOp.Hold("h1", ROHAN, 40))) as LedgerResult.Applied
        assertEquals(once, twice.ledger)
        assertEquals(1, twice.skippedDuplicates)
    }

    @Test
    fun `a failing op leaves the whole batch unapplied`() {
        val result = start.applyAll(listOf(CoinOp.Hold("h1", ROHAN, 10), CoinOp.Release("r", ROHAN, 99)))
        assertTrue(result is LedgerResult.Failed)
        assertEquals(100, start.availableOf(ROHAN)) // original is untouched (immutable)
    }

    @Test
    fun `zero or negative amounts are refused`() {
        val failed = start.applyAll(listOf(CoinOp.Hold("h", ROHAN, 0))) as LedgerResult.Failed
        assertEquals(LedgerError.NON_POSITIVE_AMOUNT, failed.error)
    }
}
