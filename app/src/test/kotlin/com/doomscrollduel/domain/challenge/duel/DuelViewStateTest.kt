package com.doomscrollduel.domain.challenge.duel

import com.doomscrollduel.domain.challenge.AMAN
import com.doomscrollduel.domain.challenge.ChallengeId
import com.doomscrollduel.domain.challenge.ChallengeMode
import com.doomscrollduel.domain.challenge.RIYA
import com.doomscrollduel.domain.challenge.ROHAN
import com.doomscrollduel.domain.challenge.T0
import com.doomscrollduel.domain.challenge.hours
import com.doomscrollduel.domain.challenge.minutes
import org.junit.Assert.assertEquals
import org.junit.Test

class DuelViewStateTest {
    private val config = DuelConfig(100, DuelDuration.ONE_DAY, 50)
    private val invite = Duel.invite(ChallengeId("d"), ChallengeMode.DUEL, ROHAN, AMAN, config, T0)
    private fun view(duel: Duel, who: com.doomscrollduel.domain.challenge.PlayerId, now: java.time.Instant = T0, mine: Int = 0, theirs: Int = 0) =
        DuelViews.forViewer(duel, who, now, mine, theirs)

    @Test
    fun `invite shows differently to the sender and the receiver`() {
        val now = T0.plus(hours(2))
        assertEquals(DuelViewState.InviteReceived(hours(22), 50, 100, DuelDuration.ONE_DAY), view(invite, AMAN, now))
        assertEquals(DuelViewState.InviteSent(hours(22)), view(invite, ROHAN, now))
    }

    @Test
    fun `time left on an invite never goes below zero`() {
        assertEquals(DuelViewState.InviteSent(java.time.Duration.ZERO), view(invite, ROHAN, T0.plus(hours(30))))
    }

    @Test
    fun `a stranger sees nothing`() {
        assertEquals(DuelViewState.NotMine, view(invite, RIYA))
    }

    private val active = invite.copy(state = DuelState.Active(T0, T0.plus(hours(24))))

    @Test
    fun `running shows live counts, the pot and who leads`() {
        val running = view(active, ROHAN, T0.plus(hours(1)), mine = 31, theirs = 47) as DuelViewState.Running
        assertEquals(hours(23), running.timeLeft)
        assertEquals(LeadView.You(16), running.lead)
        assertEquals(100, running.pot)
        val opponentView = view(active, AMAN, T0.plus(hours(1)), mine = 47, theirs = 31) as DuelViewState.Running
        assertEquals(LeadView.Opponent(16), opponentView.lead)
    }

    @Test
    fun `lead is tied on equal counts`() {
        assertEquals(LeadView.Tied, DuelViews.lead(5, 5))
    }

    @Test
    fun `result views are mirrored for winner and loser`() {
        val win = DuelResult.Win(ROHAN, AMAN, 31, 47, WinReason.LOWER_COUNT)
        val finished = invite.copy(state = DuelState.Finished(T0, win))
        assertEquals(DuelViewState.Won(31, 47, coinsWon = 50, byForfeit = false), view(finished, ROHAN))
        assertEquals(DuelViewState.Lost(47, 31, coinsLost = 50, byForfeit = false), view(finished, AMAN))
    }

    @Test
    fun `forfeit results say so`() {
        val win = DuelResult.Win(ROHAN, AMAN, 12, 3, WinReason.OPPONENT_FORFEIT)
        val forfeited = invite.copy(state = DuelState.Forfeited(T0, win))
        assertEquals(DuelViewState.Won(12, 3, 50, byForfeit = true), view(forfeited, ROHAN))
        assertEquals(DuelViewState.Lost(3, 12, 50, byForfeit = true), view(forfeited, AMAN))
    }

    @Test
    fun `tie and mutual forfeit return the stake`() {
        assertEquals(DuelViewState.Tied(40, 50), view(invite.copy(state = DuelState.Finished(T0, DuelResult.Tie(40))), ROHAN))
        assertEquals(DuelViewState.MutualForfeit(50), view(invite.copy(state = DuelState.Forfeited(T0, DuelResult.MutualForfeit)), AMAN))
    }

    @Test
    fun `cancelled and expired`() {
        val declined = invite.copy(state = DuelState.Cancelled(T0, AMAN, CancelReason.DECLINED))
        assertEquals(DuelViewState.Cancelled(CancelReason.DECLINED, byMe = true), view(declined, AMAN))
        assertEquals(DuelViewState.Cancelled(CancelReason.DECLINED, byMe = false), view(declined, ROHAN))
        val system = invite.copy(state = DuelState.Cancelled(T0, null, CancelReason.INSUFFICIENT_FUNDS))
        assertEquals(DuelViewState.Cancelled(CancelReason.INSUFFICIENT_FUNDS, byMe = false), view(system, ROHAN))
        assertEquals(DuelViewState.Expired, view(invite.copy(state = DuelState.Expired(T0.plus(minutes(1)))), AMAN))
    }
}
