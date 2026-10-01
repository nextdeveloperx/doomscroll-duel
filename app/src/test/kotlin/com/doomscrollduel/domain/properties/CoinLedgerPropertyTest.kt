package com.doomscrollduel.domain.properties

import com.doomscrollduel.domain.challenge.CoinLedger
import com.doomscrollduel.domain.challenge.CoinOp
import com.doomscrollduel.domain.challenge.LedgerResult
import com.doomscrollduel.domain.challenge.PlayerId
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Thousands of random batches against the coin ledger. Seeds are fixed, so a failure message names the seed and can be
 * replayed exactly. The rules under test: coins are only ever moved (never made or destroyed), no balance goes below
 * zero, a batch applies completely or not at all, and replaying an operation changes nothing.
 */
class CoinLedgerPropertyTest {
    private val players = listOf("a", "b", "c", "d").map(::PlayerId)

    private fun randomOp(rnd: Random, id: String): CoinOp {
        val p = players[rnd.nextInt(players.size)]
        val q = players[rnd.nextInt(players.size)]
        val amount = rnd.nextInt(-5, 120) // includes zero and negative on purpose
        return when (rnd.nextInt(3)) {
            0 -> CoinOp.Hold(id, p, amount)
            1 -> CoinOp.Release(id, p, amount)
            else -> CoinOp.Transfer(id, p, q, amount)
        }
    }

    private fun startLedger(rnd: Random) =
        CoinLedger(available = players.associateWith { rnd.nextInt(0, 500) })

    private fun assertSane(ledger: CoinLedger, seed: Int) {
        players.forEach {
            assertTrue("negative available, seed=$seed", ledger.availableOf(it) >= 0)
            assertTrue("negative held, seed=$seed", ledger.heldOf(it) >= 0)
        }
    }

    @Test
    fun `coins are never created or destroyed, and no balance goes negative`() {
        for (seed in 1..400) {
            val rnd = Random(seed)
            var ledger = startLedger(rnd)
            val total = ledger.total
            repeat(40) { step ->
                val batch = List(rnd.nextInt(1, 5)) { randomOp(rnd, "s$seed-$step-$it") }
                when (val r = ledger.applyAll(batch)) {
                    is LedgerResult.Applied -> ledger = r.ledger
                    is LedgerResult.Failed -> Unit
                }
                assertEquals("total changed, seed=$seed step=$step", total, ledger.total)
                assertSane(ledger, seed)
            }
        }
    }

    @Test
    fun `a failing batch leaves the ledger exactly as it was`() {
        for (seed in 1..300) {
            val rnd = Random(seed)
            val ledger = startLedger(rnd)
            // One good op followed by one that cannot work (a negative amount).
            val good = CoinOp.Hold("g$seed", players[0], 1.coerceAtMost(ledger.availableOf(players[0])).coerceAtLeast(0).let { if (it == 0) 1 else it })
            val bad = CoinOp.Hold("b$seed", players[1], -1)
            val result = ledger.applyAll(listOf(good, bad))
            assertTrue("seed=$seed", result is LedgerResult.Failed)
            // The original ledger value is untouched (it is immutable) and a retry without the bad op still works on it.
            assertEquals(startLedger(Random(seed)), ledger)
        }
    }

    @Test
    fun `replaying the same operations is harmless`() {
        for (seed in 1..300) {
            val rnd = Random(seed)
            var ledger = startLedger(rnd)
            val applied = mutableListOf<CoinOp>()
            repeat(30) { step ->
                val op = randomOp(rnd, "r$seed-$step")
                val r = ledger.applyAll(listOf(op))
                if (r is LedgerResult.Applied) {
                    ledger = r.ledger
                    applied += op
                }
            }
            val again = ledger.applyAll(applied) as LedgerResult.Applied
            assertEquals("replay changed the ledger, seed=$seed", ledger.available, again.ledger.available)
            assertEquals(ledger.held, again.ledger.held)
            assertEquals(applied.size, again.skippedDuplicates)
        }
    }

    @Test
    fun `hold then transfer then release returns exactly the stake to the winner`() {
        for (stake in listOf(1, 25, 50, 100, 1000)) {
            val loser = PlayerId("loser")
            val winner = PlayerId("winner")
            var ledger = CoinLedger(available = mapOf(loser to stake, winner to stake))
            ledger = (ledger.applyAll(
                listOf(CoinOp.Hold("h1", loser, stake), CoinOp.Hold("h2", winner, stake)),
            ) as LedgerResult.Applied).ledger
            ledger = (ledger.applyAll(
                listOf(CoinOp.Transfer("t", loser, winner, stake), CoinOp.Release("r", winner, stake)),
            ) as LedgerResult.Applied).ledger
            assertEquals(0, ledger.availableOf(loser))
            assertEquals(stake * 2, ledger.availableOf(winner))
            assertEquals(0, ledger.heldOf(loser) + ledger.heldOf(winner))
        }
    }
}
