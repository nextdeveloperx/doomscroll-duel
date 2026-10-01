package com.doomscrollduel.domain.challenge.squad

import com.doomscrollduel.domain.challenge.ChallengeId
import com.doomscrollduel.domain.challenge.DuelStatus
import com.doomscrollduel.domain.challenge.GapCause
import com.doomscrollduel.domain.challenge.PlayerId
import com.doomscrollduel.domain.challenge.T0
import com.doomscrollduel.domain.challenge.TrackingGap
import com.doomscrollduel.domain.challenge.duel.DuelDuration
import com.doomscrollduel.domain.challenge.hours
import com.doomscrollduel.domain.challenge.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SquadScoringTest {
    private fun p(n: Int) = PlayerId("p$n")
    private fun score(id: Int, reels: Int, dq: Boolean = false, limit: Int = 100) =
        MemberScore(p(id), reels, dq, if (dq) maxOf(reels, limit) else reels)

    private fun team(side: SquadSide, vararg reels: Int) =
        SquadScoring.teamScore(side, reels.mapIndexed { i, r -> score(i, r) })

    @Test
    fun `lower average wins`() {
        // A: (10+20+30)/3 = 20, B: (25+25+25)/3 = 25
        val result = SquadScoring.decide(team(SquadSide.A, 10, 20, 30), team(SquadSide.B, 25, 25, 25))
        assertEquals(SquadResult.Win(SquadSide.A, SquadDecider.LOWER_AVERAGE), result)
    }

    @Test
    fun `teams of different sizes are compared by average not by total`() {
        // A has 3 members with 60 total (avg 20); B has 10 members with 150 total (avg 15). B wins although its total is higher.
        val a = team(SquadSide.A, 20, 20, 20)
        val b = team(SquadSide.B, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15)
        assertEquals(SquadResult.Win(SquadSide.B, SquadDecider.LOWER_AVERAGE), SquadScoring.decide(a, b))
    }

    @Test
    fun `the comparison is exact, with no rounding`() {
        // 10/3 = 3.3333 vs 3.3334: use totals that differ by one over a big team.
        val a = TeamScore(SquadSide.A, total = 10, members = 3, worstMember = 5)
        val b = TeamScore(SquadSide.B, total = 33_334, members = 10_002, worstMember = 5)
        // 10*10002 = 100020 vs 33334*3 = 100002: B is lower.
        assertEquals(SquadSide.B, (SquadScoring.decide(a, b) as SquadResult.Win).side)
    }

    @Test
    fun `equal averages are decided by the weakest member`() {
        // Both average 20. A's worst is 40, B's worst is 25.
        val a = team(SquadSide.A, 0, 20, 40)
        val b = team(SquadSide.B, 15, 20, 25)
        assertEquals(SquadResult.Win(SquadSide.B, SquadDecider.LOWER_WORST_MEMBER), SquadScoring.decide(a, b))
    }

    @Test
    fun `same average and same weakest member is a draw`() {
        assertEquals(SquadResult.Draw, SquadScoring.decide(team(SquadSide.A, 10, 20, 30), team(SquadSide.B, 30, 10, 20)))
    }

    @Test
    fun `a disqualified member is scored at the limit or their real count if higher`() {
        val start = T0
        val end = T0.plus(hours(24))
        val off = TrackingGap(start.plus(minutes(30)), start.plus(minutes(60)), GapCause.SERVICE_DISABLED)
        val reports = mapOf(
            p(1) to MemberReport(5),
            p(2) to MemberReport(8, listOf(off)), // counter off 30 minutes
            p(3) to MemberReport(150, listOf(off)), // off too, and really watched more than the limit
            // p(4) never reported
        )
        val scores = SquadScoring.memberScores(listOf(p(1), p(2), p(3), p(4)), reports, limit = 100, startedAt = start, endsAt = end)
        assertEquals(listOf(5, 100, 150, 100), scores.map { it.effectiveReels })
        assertEquals(listOf(false, true, true, true), scores.map { it.disqualified })
    }

    @Test
    fun `a short gap does not disqualify`() {
        val start = T0
        val short = TrackingGap(start.plus(minutes(30)), start.plus(minutes(40)), GapCause.SERVICE_DISABLED)
        val scores = SquadScoring.memberScores(listOf(p(1)), mapOf(p(1) to MemberReport(7, listOf(short))), 100, start, start.plus(hours(6)))
        assertFalse(scores.single().disqualified)
        assertEquals(7, scores.single().effectiveReels)
    }

    @Test
    fun `leaderboard ranks fewest first and highlights the weakest member`() {
        val rows = SquadScoring.leaderboard(listOf(score(1, 40), score(2, 5), score(3, 90), score(4, 5)))
        assertEquals(listOf(p(2), p(4), p(1), p(3)), rows.map { it.player })
        assertEquals(listOf(1, 1, 3, 4), rows.map { it.rank })
        assertEquals(listOf(false, false, false, true), rows.map { it.isWeakest })
    }

    @Test
    fun `tied weakest members are all highlighted`() {
        val rows = SquadScoring.leaderboard(listOf(score(1, 10), score(2, 70), score(3, 70)))
        assertEquals(listOf(false, true, true), rows.map { it.isWeakest })
    }

    @Test
    fun `nobody is singled out when everyone is equal`() {
        val rows = SquadScoring.leaderboard(listOf(score(1, 0), score(2, 0), score(3, 0)))
        assertTrue(rows.none { it.isWeakest })
        assertTrue(SquadScoring.leaderboard(emptyList()).isEmpty())
    }
}

class SquadLifecycleTest {
    private val leaderA = PlayerId("la")
    private val leaderB = PlayerId("lb")
    private val config = SquadBattleConfig(reelLimitPerMember = 100, duration = DuelDuration.ONE_DAY)
    private fun m(prefix: String, n: Int) = PlayerId("$prefix$n")

    private fun moved(t: SquadTransition) = (t as SquadTransition.Moved).battle
    private fun rejected(t: SquadTransition) = (t as SquadTransition.Rejected).reason

    private fun newBattle() = SquadBattle.create(ChallengeId("s1"), leaderA, leaderIsPro = true, "A-team", config, T0)!!

    private fun step(b: SquadBattle, e: SquadEvent) = moved(SquadStateMachine.reduce(b, e))

    private fun readyBattle(sizeA: Int = 3, sizeB: Int = 3): SquadBattle {
        var b = newBattle()
        b = step(b, SquadEvent.ClaimSideB(leaderB, true, "B-team", T0))
        repeat(sizeA - 1) { b = step(b, SquadEvent.Join(SquadSide.A, m("a", it), T0)) }
        repeat(sizeB - 1) { b = step(b, SquadEvent.Join(SquadSide.B, m("b", it), T0)) }
        return b
    }

    @Test
    fun `only a Pro leader can create a squad battle`() {
        assertNull(SquadBattle.create(ChallengeId("s"), leaderA, leaderIsPro = false, "x", config, T0))
        assertEquals(DuelStatus.PENDING, newBattle().state.status)
    }

    @Test
    fun `the other leader also needs Pro to claim side B, members never do`() {
        val b = newBattle()
        assertEquals(SquadReject.LEADER_NEEDS_PRO, rejected(SquadStateMachine.reduce(b, SquadEvent.ClaimSideB(leaderB, false, "B", T0))))
        val withB = step(b, SquadEvent.ClaimSideB(leaderB, true, "B", T0))
        assertEquals(SquadReject.SIDE_ALREADY_CLAIMED, rejected(SquadStateMachine.reduce(withB, SquadEvent.ClaimSideB(PlayerId("x"), true, "B2", T0))))
        // A free member joins without Pro (the event has no Pro flag at all).
        assertEquals(2, step(withB, SquadEvent.Join(SquadSide.A, m("a", 1), T0)).squadA.members.size)
    }

    @Test
    fun `squad size is limited to 10 and 3 is needed to start`() {
        var b = readyBattle(sizeA = 10, sizeB = 3)
        assertEquals(SquadReject.SQUAD_FULL, rejected(SquadStateMachine.reduce(b, SquadEvent.Join(SquadSide.A, PlayerId("late"), T0))))
        b = readyBattle(sizeA = 2, sizeB = 3)
        assertEquals(SquadReject.ROSTER_TOO_SMALL, rejected(SquadStateMachine.reduce(b, SquadEvent.LockRoster(SquadSide.A, leaderA, T0))))
    }

    @Test
    fun `a person cannot be in both squads or twice`() {
        val b = readyBattle()
        assertEquals(SquadReject.ALREADY_IN_BATTLE, rejected(SquadStateMachine.reduce(b, SquadEvent.Join(SquadSide.B, m("a", 0), T0))))
        assertEquals(SquadReject.ALREADY_IN_BATTLE, rejected(SquadStateMachine.reduce(b, SquadEvent.Join(SquadSide.A, m("a", 0), T0))))
    }

    @Test
    fun `the battle starts only when both leaders are ready`() {
        var b = readyBattle()
        b = step(b, SquadEvent.LockRoster(SquadSide.A, leaderA, T0))
        assertEquals(DuelStatus.PENDING, b.state.status)
        assertEquals(SquadReject.ROSTER_LOCKED, rejected(SquadStateMachine.reduce(b, SquadEvent.Join(SquadSide.A, PlayerId("late"), T0))))
        assertEquals(SquadReject.NOT_THE_LEADER, rejected(SquadStateMachine.reduce(b, SquadEvent.LockRoster(SquadSide.B, m("b", 0), T0))))
        val at = T0.plus(minutes(30))
        b = step(b, SquadEvent.LockRoster(SquadSide.B, leaderB, at))
        val state = b.state as SquadBattleState.Active
        assertEquals(at, state.startedAt)
        assertEquals(at.plus(hours(24)), state.endsAt)
    }

    @Test
    fun `members can leave before lock but the leader cannot`() {
        var b = readyBattle()
        assertEquals(SquadReject.LEADER_CANNOT_LEAVE, rejected(SquadStateMachine.reduce(b, SquadEvent.Leave(SquadSide.A, leaderA, T0))))
        b = step(b, SquadEvent.Leave(SquadSide.A, m("a", 0), T0))
        assertEquals(2, b.squadA.members.size)
    }

    @Test
    fun `the lobby expires after 24 hours and a leader can cancel`() {
        val b = readyBattle()
        assertEquals(SquadReject.TOO_EARLY, rejected(SquadStateMachine.reduce(b, SquadEvent.Expire(T0.plus(hours(23))))))
        assertEquals(SquadReject.LOBBY_EXPIRED, rejected(SquadStateMachine.reduce(b, SquadEvent.Join(SquadSide.A, PlayerId("late"), T0.plus(hours(24))))))
        assertEquals(DuelStatus.EXPIRED, step(b, SquadEvent.Expire(T0.plus(hours(24)))).state.status)
        assertEquals(DuelStatus.CANCELLED, step(b, SquadEvent.Cancel(leaderA, T0)).state.status)
        assertEquals(SquadReject.NOT_THE_LEADER, rejected(SquadStateMachine.reduce(b, SquadEvent.Cancel(m("a", 0), T0))))
    }

    private fun started(): SquadBattle {
        var b = readyBattle()
        b = step(b, SquadEvent.LockRoster(SquadSide.A, leaderA, T0))
        return step(b, SquadEvent.LockRoster(SquadSide.B, leaderB, T0))
    }

    private fun reports(a: List<Int>, b: List<Int>): Map<PlayerId, MemberReport> {
        val map = mutableMapOf<PlayerId, MemberReport>()
        val battle = started()
        battle.squadA.members.forEachIndexed { i, p -> map[p] = MemberReport(a[i]) }
        battle.squadB!!.members.forEachIndexed { i, p -> map[p] = MemberReport(b[i]) }
        return map
    }

    @Test
    fun `settling decides the winner by average and finishes the battle`() {
        val b = started()
        val end = (b.state as SquadBattleState.Active).endsAt
        val finished = step(b, SquadEvent.Settle(end, reports(listOf(10, 20, 30), listOf(40, 40, 40))))
        assertEquals(SquadResult.Win(SquadSide.A, SquadDecider.LOWER_AVERAGE), (finished.state as SquadBattleState.Finished).result)
    }

    @Test
    fun `a member who never reported counts against their squad`() {
        val b = started()
        val end = (b.state as SquadBattleState.Active).endsAt
        val r = reports(listOf(0, 0, 0), listOf(30, 30, 30)).toMutableMap()
        r.remove(b.squadA.members[1]) // one member of A is silent: scored at the limit (100)
        val finished = step(b, SquadEvent.Settle(end, r))
        // A: (0+100+0)/3 = 33.3, B: 30 -> B wins.
        assertEquals(SquadSide.B, ((finished.state as SquadBattleState.Finished).result as SquadResult.Win).side)
    }

    @Test
    fun `settle too early is refused and a second settle is refused`() {
        val b = started()
        val end = (b.state as SquadBattleState.Active).endsAt
        assertEquals(SquadReject.TOO_EARLY, rejected(SquadStateMachine.reduce(b, SquadEvent.Settle(end.minusSeconds(1), emptyMap()))))
        val done = step(b, SquadEvent.Settle(end, reports(listOf(1, 1, 1), listOf(2, 2, 2))))
        assertEquals(SquadReject.ALREADY_FINAL, rejected(SquadStateMachine.reduce(done, SquadEvent.Settle(end, reports(listOf(9, 9, 9), listOf(1, 1, 1))))))
        assertEquals(SquadReject.ALREADY_FINAL, rejected(SquadStateMachine.reduce(done, SquadEvent.Cancel(leaderA, end))))
    }
}
