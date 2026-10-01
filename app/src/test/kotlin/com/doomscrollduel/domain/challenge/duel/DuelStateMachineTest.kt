package com.doomscrollduel.domain.challenge.duel

import com.doomscrollduel.domain.challenge.AMAN
import com.doomscrollduel.domain.challenge.ChallengeId
import com.doomscrollduel.domain.challenge.ChallengeMode
import com.doomscrollduel.domain.challenge.CoinLedger
import com.doomscrollduel.domain.challenge.DuelStatus
import com.doomscrollduel.domain.challenge.GapCause
import com.doomscrollduel.domain.challenge.LedgerResult
import com.doomscrollduel.domain.challenge.RIYA
import com.doomscrollduel.domain.challenge.ROHAN
import com.doomscrollduel.domain.challenge.T0
import com.doomscrollduel.domain.challenge.TrackingGap
import com.doomscrollduel.domain.challenge.hours
import com.doomscrollduel.domain.challenge.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuelStateMachineTest {
    private val config = DuelConfig(reelLimit = 100, duration = DuelDuration.ONE_DAY, stake = 50)
    private val invite = Duel.invite(ChallengeId("d1"), ChallengeMode.DUEL, ROHAN, AMAN, config, T0)
    private val acceptedAt = T0.plus(hours(2))

    private fun moved(t: Transition) = t as Transition.Moved
    private fun rejected(t: Transition) = (t as Transition.Rejected).reason

    private fun accept(duel: Duel = invite, at: java.time.Instant = acceptedAt, by: com.doomscrollduel.domain.challenge.PlayerId = AMAN) =
        DuelStateMachine.reduce(duel, DuelEvent.Accept(by, at, creatorAvailable = 200, opponentAvailable = 200))

    private fun active(): Duel = moved(accept()).duel
    private val ledger = CoinLedger(available = mapOf(ROHAN to 200, AMAN to 200))

    // ----- invite -----------------------------------------------------------------------------

    @Test
    fun `a new invite is pending and expires in 24 hours`() {
        val state = invite.state as DuelState.Pending
        assertEquals(DuelStatus.PENDING, state.status)
        assertEquals(T0.plus(hours(24)), state.expiresAt)
    }

    @Test
    fun `accepting starts the clock and holds both stakes`() {
        val t = moved(accept())
        val state = t.duel.state as DuelState.Active
        assertEquals(acceptedAt, state.startedAt)
        assertEquals(acceptedAt.plus(hours(24)), state.endsAt)
        val held = (ledger.applyAll(t.ops) as LedgerResult.Applied).ledger
        assertEquals(150, held.availableOf(ROHAN))
        assertEquals(50, held.heldOf(ROHAN))
        assertEquals(50, held.heldOf(AMAN))
    }

    @Test
    fun `the duration decides the end time`() {
        for (d in DuelDuration.entries) {
            val duel = invite.copy(config = config.copy(duration = d))
            val state = moved(accept(duel)).duel.state as DuelState.Active
            assertEquals(acceptedAt.plus(d.duration), state.endsAt)
        }
    }

    @Test
    fun `only the invited player can accept or decline, only the creator can cancel`() {
        assertEquals(RejectReason.NOT_THE_OPPONENT, rejected(accept(by = ROHAN)))
        assertEquals(RejectReason.NOT_THE_OPPONENT, rejected(DuelStateMachine.reduce(invite, DuelEvent.Decline(RIYA, acceptedAt))))
        assertEquals(RejectReason.NOT_THE_CREATOR, rejected(DuelStateMachine.reduce(invite, DuelEvent.Cancel(AMAN, acceptedAt))))
    }

    @Test
    fun `accept after 24 hours is refused and the invite expires instead`() {
        val late = T0.plus(hours(24))
        assertEquals(RejectReason.INVITE_EXPIRED, rejected(accept(at = late)))
        val expired = moved(DuelStateMachine.reduce(invite, DuelEvent.Expire(late))).duel
        assertTrue(expired.state is DuelState.Expired)
        assertEquals(DuelStatus.EXPIRED, expired.state.status)
    }

    @Test
    fun `expire too early is refused`() {
        assertEquals(RejectReason.TOO_EARLY, rejected(DuelStateMachine.reduce(invite, DuelEvent.Expire(T0.plus(hours(23))))))
    }

    @Test
    fun `decline and creator cancel end it with no coins`() {
        val declined = moved(DuelStateMachine.reduce(invite, DuelEvent.Decline(AMAN, acceptedAt)))
        assertEquals(CancelReason.DECLINED, (declined.duel.state as DuelState.Cancelled).reason)
        assertTrue(declined.ops.isEmpty())
        val cancelled = moved(DuelStateMachine.reduce(invite, DuelEvent.Cancel(ROHAN, acceptedAt)))
        assertEquals(CancelReason.CREATOR_CANCELLED, (cancelled.duel.state as DuelState.Cancelled).reason)
        assertTrue(cancelled.ops.isEmpty())
    }

    @Test
    fun `not enough coins at accept cancels the duel and holds nothing`() {
        for ((creatorCoins, opponentCoins) in listOf(49 to 200, 200 to 49, 0 to 0)) {
            val t = moved(DuelStateMachine.reduce(invite, DuelEvent.Accept(AMAN, acceptedAt, creatorCoins, opponentCoins)))
            val state = t.duel.state as DuelState.Cancelled
            assertEquals(CancelReason.INSUFFICIENT_FUNDS, state.reason)
            assertEquals(null, state.by)
            assertTrue(t.ops.isEmpty())
        }
    }

    @Test
    fun `exactly enough coins is enough`() {
        val t = moved(DuelStateMachine.reduce(invite, DuelEvent.Accept(AMAN, acceptedAt, 50, 50)))
        assertTrue(t.duel.state is DuelState.Active)
    }

    @Test
    fun `an active duel cannot be cancelled or declined`() {
        val duel = active()
        assertEquals(RejectReason.WRONG_STATE, rejected(DuelStateMachine.reduce(duel, DuelEvent.Cancel(ROHAN, acceptedAt))))
        assertEquals(RejectReason.WRONG_STATE, rejected(DuelStateMachine.reduce(duel, DuelEvent.Decline(AMAN, acceptedAt))))
        assertEquals(RejectReason.WRONG_STATE, rejected(accept(duel)))
        assertEquals(RejectReason.WRONG_STATE, rejected(DuelStateMachine.reduce(duel, DuelEvent.Expire(T0.plus(hours(48))))))
    }

    // ----- settle -----------------------------------------------------------------------------

    private fun settle(duel: Duel, rohan: PlayerScore, aman: PlayerScore, at: java.time.Instant? = null): Transition {
        // A finished duel has no end time any more; use a time well after any duel could have ended.
        val end = (duel.state as? DuelState.Active)?.endsAt ?: T0.plus(com.doomscrollduel.domain.challenge.hours(24 * 30))
        return DuelStateMachine.reduce(duel, DuelEvent.Settle(at ?: end, rohan, aman))
    }

    @Test
    fun `settle before the end is refused`() {
        val duel = active()
        val end = (duel.state as DuelState.Active).endsAt
        assertEquals(RejectReason.TOO_EARLY, rejected(settle(duel, PlayerScore(ROHAN, 1), PlayerScore(AMAN, 2), end.minusSeconds(1))))
    }

    @Test
    fun `lower count finishes the duel and pays the winner`() {
        val duel = active()
        val t = moved(settle(duel, PlayerScore(ROHAN, 31), PlayerScore(AMAN, 47)))
        val state = t.duel.state as DuelState.Finished
        assertEquals(DuelStatus.FINISHED, state.status)
        assertEquals(ROHAN, (state.result as DuelResult.Win).winner)

        val held = (ledger.applyAll(moved(accept()).ops) as LedgerResult.Applied).ledger
        val paid = (held.applyAll(t.ops) as LedgerResult.Applied).ledger
        assertEquals(250, paid.availableOf(ROHAN)) // 200 - 50 held + 100 pot
        assertEquals(150, paid.availableOf(AMAN))
        assertEquals(0, paid.heldOf(ROHAN) + paid.heldOf(AMAN))
        assertEquals(ledger.total, paid.total)
    }

    @Test
    fun `a tie finishes the duel and returns both stakes`() {
        val duel = active()
        val t = moved(settle(duel, PlayerScore(ROHAN, 40), PlayerScore(AMAN, 40)))
        assertTrue(t.duel.state is DuelState.Finished)
        val held = (ledger.applyAll(moved(accept()).ops) as LedgerResult.Applied).ledger
        val back = (held.applyAll(t.ops) as LedgerResult.Applied).ledger
        assertEquals(200, back.availableOf(ROHAN))
        assertEquals(200, back.availableOf(AMAN))
    }

    @Test
    fun `a forfeit found at settlement ends it as forfeited`() {
        val duel = active()
        val start = (duel.state as DuelState.Active).startedAt
        val gap = TrackingGap(start.plus(minutes(30)), start.plus(minutes(50)), GapCause.SERVICE_DISABLED)
        val t = moved(settle(duel, PlayerScore(ROHAN, 90), PlayerScore(AMAN, 5, listOf(gap))))
        val state = t.duel.state as DuelState.Forfeited
        assertEquals(DuelStatus.FORFEITED, state.status)
        assertEquals(ROHAN, (state.result as DuelResult.Win).winner)
    }

    @Test
    fun `a mutual forfeit ends forfeited with both stakes returned`() {
        val duel = active()
        val start = (duel.state as DuelState.Active).startedAt
        val gap = TrackingGap(start.plus(minutes(30)), start.plus(minutes(50)), GapCause.SERVICE_DISABLED)
        val t = moved(settle(duel, PlayerScore(ROHAN, 1, listOf(gap)), PlayerScore(AMAN, 2, listOf(gap))))
        assertEquals(DuelResult.MutualForfeit, (t.duel.state as DuelState.Forfeited).result)
    }

    // ----- live forfeit -----------------------------------------------------------------------

    @Test
    fun `counter off over 10 minutes forfeits right away`() {
        val duel = active()
        val start = (duel.state as DuelState.Active).startedAt
        val gap = TrackingGap(start.plus(minutes(60)), null, GapCause.SERVICE_DISABLED)
        val at = start.plus(minutes(75))
        val t = moved(DuelStateMachine.reduce(duel, DuelEvent.TrackingOffTooLong(AMAN, gap, at, creatorReels = 12, opponentReels = 3)))
        val state = t.duel.state as DuelState.Forfeited
        val result = state.result as DuelResult.Win
        assertEquals(ROHAN, result.winner)
        assertEquals(AMAN, result.loser)
        assertEquals(12, result.winnerReels)
        assertEquals(3, result.loserReels)
        assertEquals(at, state.settledAt)
        assertEquals(2, t.ops.size)
    }

    @Test
    fun `a gap of 10 minutes or less is not enough evidence`() {
        val duel = active()
        val start = (duel.state as DuelState.Active).startedAt
        val gap = TrackingGap(start.plus(minutes(60)), null, GapCause.SERVICE_DISABLED)
        val at = start.plus(minutes(70))
        assertEquals(
            RejectReason.EVIDENCE_TOO_SHORT,
            rejected(DuelStateMachine.reduce(duel, DuelEvent.TrackingOffTooLong(AMAN, gap, at, 0, 0))),
        )
        val dead = TrackingGap(start.plus(minutes(60)), null, GapCause.DEVICE_OFF_OR_UNKNOWN)
        assertEquals(
            RejectReason.EVIDENCE_TOO_SHORT,
            rejected(DuelStateMachine.reduce(duel, DuelEvent.TrackingOffTooLong(AMAN, dead, start.plus(minutes(500)), 0, 0))),
        )
    }

    @Test
    fun `a stranger cannot forfeit a player`() {
        val duel = active()
        val start = (duel.state as DuelState.Active).startedAt
        val gap = TrackingGap(start, null, GapCause.SERVICE_DISABLED)
        assertEquals(
            RejectReason.NOT_A_PLAYER,
            rejected(DuelStateMachine.reduce(duel, DuelEvent.TrackingOffTooLong(RIYA, gap, start.plus(minutes(60)), 0, 0))),
        )
    }

    // ----- double settlement ------------------------------------------------------------------

    @Test
    fun `settling twice is refused the second time`() {
        val duel = active()
        val first = moved(settle(duel, PlayerScore(ROHAN, 31), PlayerScore(AMAN, 47)))
        val second = settle(first.duel, PlayerScore(ROHAN, 31), PlayerScore(AMAN, 47))
        assertEquals(RejectReason.ALREADY_FINAL, rejected(second))
    }

    @Test
    fun `a different second result cannot overwrite the first`() {
        val duel = active()
        val first = moved(settle(duel, PlayerScore(ROHAN, 31), PlayerScore(AMAN, 47)))
        val flipped = settle(first.duel, PlayerScore(ROHAN, 99), PlayerScore(AMAN, 1))
        assertEquals(RejectReason.ALREADY_FINAL, rejected(flipped))
        assertEquals(ROHAN, ((first.duel.state as DuelState.Finished).result as DuelResult.Win).winner)
    }

    @Test
    fun `a stale settlement racing a live forfeit cannot pay a second time`() {
        val duel = active()
        val start = (duel.state as DuelState.Active).startedAt
        val gap = TrackingGap(start.plus(minutes(10)), null, GapCause.SERVICE_DISABLED)
        val forfeit = moved(DuelStateMachine.reduce(duel, DuelEvent.TrackingOffTooLong(AMAN, gap, start.plus(minutes(30)), 5, 6)))
        // Another server still holds the old ACTIVE copy and settles it with different numbers (Aman wins on count).
        val stale = moved(settle(duel, PlayerScore(ROHAN, 99), PlayerScore(AMAN, 1)))

        val held = (ledger.applyAll(moved(accept()).ops) as LedgerResult.Applied).ledger
        val once = (held.applyAll(forfeit.ops) as LedgerResult.Applied).ledger
        assertEquals(250, once.availableOf(ROHAN))

        // The state machine would refuse the stale copy (ALREADY_FINAL on the stored duel); even if the ops
        // arrive anyway, the ledger rejects them: Aman's escrow was already paid out.
        assertTrue(once.applyAll(stale.ops) is LedgerResult.Failed)
        assertEquals(250, once.availableOf(ROHAN))
        assertEquals(150, once.availableOf(AMAN))
    }

    @Test
    fun `replaying the same coin ops cannot pay twice`() {
        val duel = active()
        val settled = moved(settle(duel, PlayerScore(ROHAN, 31), PlayerScore(AMAN, 47)))
        val held = (ledger.applyAll(moved(accept()).ops) as LedgerResult.Applied).ledger
        val once = (held.applyAll(settled.ops) as LedgerResult.Applied).ledger
        val again = once.applyAll(settled.ops) as LedgerResult.Applied
        assertEquals(once, again.ledger)
        assertEquals(settled.ops.size, again.skippedDuplicates)
        assertEquals(250, again.ledger.availableOf(ROHAN))
    }

    @Test
    fun `every terminal state refuses every event`() {
        val terminal = listOf(
            moved(DuelStateMachine.reduce(invite, DuelEvent.Decline(AMAN, T0))).duel,
            moved(DuelStateMachine.reduce(invite, DuelEvent.Expire(T0.plus(hours(25))))).duel,
            moved(settle(active(), PlayerScore(ROHAN, 1), PlayerScore(AMAN, 2))).duel,
        )
        for (duel in terminal) {
            assertTrue(duel.state.status.isTerminal)
            assertEquals(RejectReason.ALREADY_FINAL, rejected(accept(duel)))
            assertEquals(RejectReason.ALREADY_FINAL, rejected(DuelStateMachine.reduce(duel, DuelEvent.Expire(T0.plus(hours(99))))))
        }
    }

    // ----- validation -------------------------------------------------------------------------

    @Test
    fun `a duel needs a stake and a dare duel must have none`() {
        val dareConfig = config.copy(stake = 0)
        Duel.invite(ChallengeId("x"), ChallengeMode.FORFEIT_DARE, ROHAN, AMAN, dareConfig, T0)
        val failures = listOf<() -> Unit>(
            { Duel.invite(ChallengeId("x"), ChallengeMode.DUEL, ROHAN, AMAN, dareConfig, T0) },
            { Duel.invite(ChallengeId("x"), ChallengeMode.FORFEIT_DARE, ROHAN, AMAN, config, T0) },
            { Duel.invite(ChallengeId("x"), ChallengeMode.DUEL, ROHAN, ROHAN, config, T0) },
            { Duel.invite(ChallengeId("x"), ChallengeMode.SQUAD, ROHAN, AMAN, config, T0) },
            { DuelConfig(9, DuelDuration.ONE_DAY, 10) },
            { DuelConfig(501, DuelDuration.ONE_DAY, 10) },
            { DuelConfig(100, DuelDuration.ONE_DAY, -1) },
        )
        for (f in failures) {
            try {
                f()
                throw AssertionError("expected IllegalArgumentException")
            } catch (_: IllegalArgumentException) {
            }
        }
    }
}
