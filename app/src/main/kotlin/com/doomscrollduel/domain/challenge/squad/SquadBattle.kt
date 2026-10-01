package com.doomscrollduel.domain.challenge.squad

import com.doomscrollduel.domain.challenge.ChallengeId
import com.doomscrollduel.domain.challenge.DuelStatus
import com.doomscrollduel.domain.challenge.ForfeitRule
import com.doomscrollduel.domain.challenge.PlayerId
import com.doomscrollduel.domain.challenge.ProGate
import com.doomscrollduel.domain.challenge.ChallengeMode
import com.doomscrollduel.domain.challenge.TrackingGap
import com.doomscrollduel.domain.challenge.duel.DuelDuration
import java.time.Duration
import java.time.Instant

/**
 * SQUAD BATTLE (Pro to create). Two squads of 3 to 10 face off. Each squad's score is the average
 * reels per member. A squad leader creates the squad and members join with an invite link. No coins.
 */
enum class SquadSide {
    A,
    B;

    fun other() = if (this == A) B else A
}

object SquadRules {
    const val MIN_MEMBERS = 3
    const val MAX_MEMBERS = 10

    fun isValidRoster(size: Int) = size in MIN_MEMBERS..MAX_MEMBERS
}

/** [members] includes the leader (always first). */
data class Squad(
    val name: String,
    val leader: PlayerId,
    val members: List<PlayerId>,
    /** The leader taps "ready". No more joining or leaving after that. */
    val rosterLocked: Boolean = false,
) {
    init {
        require(members.firstOrNull() == leader) { "the leader must be the first member" }
        require(members.distinct().size == members.size) { "duplicate members" }
        require(members.size <= SquadRules.MAX_MEMBERS) { "squad too big" }
    }
}

data class SquadBattleConfig(
    /** Per member. A member who is disqualified is scored at this number (or their real count if higher). */
    val reelLimitPerMember: Int,
    val duration: DuelDuration,
)

sealed interface SquadResult {
    data class Win(val side: SquadSide, val decidedBy: SquadDecider) : SquadResult

    /** Same average and same weakest member. */
    data object Draw : SquadResult
}

enum class SquadDecider {
    LOWER_AVERAGE,

    /** Averages were equal, so the squad whose worst member watched fewer reels won. */
    LOWER_WORST_MEMBER,
}

enum class SquadCancelReason { LEADER_CANCELLED }

/** `Forming (PENDING) -> Active -> Finished`, or `Forming -> Cancelled | Expired`. */
sealed interface SquadBattleState {
    val status: DuelStatus

    /** Lobby is open for 24 hours. */
    data class Forming(val openedAt: Instant, val expiresAt: Instant) : SquadBattleState {
        override val status get() = DuelStatus.PENDING
    }

    data class Active(val startedAt: Instant, val endsAt: Instant) : SquadBattleState {
        override val status get() = DuelStatus.ACTIVE
    }

    data class Finished(val settledAt: Instant, val result: SquadResult) : SquadBattleState {
        override val status get() = DuelStatus.FINISHED
    }

    data class Cancelled(val at: Instant, val by: PlayerId, val reason: SquadCancelReason) : SquadBattleState {
        override val status get() = DuelStatus.CANCELLED
    }

    data class Expired(val at: Instant) : SquadBattleState {
        override val status get() = DuelStatus.EXPIRED
    }
}

/** [squadB] is null until the other leader claims their side with the challenge link. */
data class SquadBattle(
    val id: ChallengeId,
    val config: SquadBattleConfig,
    val squadA: Squad,
    val squadB: Squad?,
    val state: SquadBattleState,
) {
    fun squad(side: SquadSide): Squad? = if (side == SquadSide.A) squadA else squadB

    fun sideOf(player: PlayerId): SquadSide? = when {
        player in squadA.members -> SquadSide.A
        squadB != null && player in squadB.members -> SquadSide.B
        else -> null
    }

    companion object {
        /** The leader needs Pro. Members never do. */
        fun create(
            id: ChallengeId,
            leader: PlayerId,
            leaderIsPro: Boolean,
            squadName: String,
            config: SquadBattleConfig,
            now: Instant,
        ): SquadBattle? {
            if (!ProGate.canCreate(ChallengeMode.SQUAD, leaderIsPro)) return null
            return SquadBattle(
                id, config,
                squadA = Squad(squadName, leader, listOf(leader)),
                squadB = null,
                state = SquadBattleState.Forming(now, now.plus(Duration.ofHours(24))),
            )
        }
    }
}

// ----- scoring ---------------------------------------------------------------------------------

/** What one member's phone reported for the battle window. A missing report is a disqualification. */
data class MemberReport(val reels: Int, val gaps: List<TrackingGap> = emptyList())

data class MemberScore(
    val player: PlayerId,
    /** Reels actually counted. */
    val reels: Int,
    /** True if the member never reported or had the counter off for over 10 minutes. */
    val disqualified: Boolean,
    /** What counts for the squad: a disqualified member is scored at the limit, or higher if they really did more. */
    val effectiveReels: Int,
)

data class TeamScore(val side: SquadSide, val total: Int, val members: Int, val worstMember: Int) {
    /** For display only. Comparisons never use this, they cross-multiply whole numbers. */
    val average: Double get() = if (members == 0) 0.0 else total.toDouble() / members
}

data class LeaderboardRow(
    val rank: Int,
    val player: PlayerId,
    val reels: Int,
    /** The member who watched the most. Highlighted on the leaderboard. Ties share the highlight. */
    val isWeakest: Boolean,
    val disqualified: Boolean,
)

object SquadScoring {

    fun memberScores(
        members: List<PlayerId>,
        reports: Map<PlayerId, MemberReport>,
        limit: Int,
        startedAt: Instant,
        endsAt: Instant,
    ): List<MemberScore> = members.map { player ->
        val report = reports[player]
        val disqualified = report == null ||
            ForfeitRule.forfeitMoment(report.gaps, startedAt, endsAt) != null
        val real = report?.reels ?: 0
        MemberScore(player, real, disqualified, effectiveReels = if (disqualified) maxOf(real, limit) else real)
    }

    fun teamScore(side: SquadSide, scores: List<MemberScore>): TeamScore = TeamScore(
        side = side,
        total = scores.sumOf { it.effectiveReels },
        members = scores.size,
        worstMember = scores.maxOfOrNull { it.effectiveReels } ?: 0,
    )

    /**
     * Lower average wins. The average is compared as total_a * members_b against total_b * members_a,
     * so teams of different sizes are compared exactly, with no rounding. If equal, the squad whose
     * worst member watched less wins. If still equal it is a draw.
     */
    fun decide(a: TeamScore, b: TeamScore): SquadResult {
        val left = a.total.toLong() * b.members
        val right = b.total.toLong() * a.members
        return when {
            left < right -> SquadResult.Win(SquadSide.A, SquadDecider.LOWER_AVERAGE)
            right < left -> SquadResult.Win(SquadSide.B, SquadDecider.LOWER_AVERAGE)
            a.worstMember < b.worstMember -> SquadResult.Win(SquadSide.A, SquadDecider.LOWER_WORST_MEMBER)
            b.worstMember < a.worstMember -> SquadResult.Win(SquadSide.B, SquadDecider.LOWER_WORST_MEMBER)
            else -> SquadResult.Draw
        }
    }

    /**
     * Fewest reels first. Members with equal reels share a rank. The weakest (most reels) is flagged,
     * unless everyone is on the same number, then nobody is singled out.
     */
    fun leaderboard(scores: List<MemberScore>): List<LeaderboardRow> {
        if (scores.isEmpty()) return emptyList()
        val sorted = scores.sortedWith(compareBy({ it.effectiveReels }, { it.player.value }))
        val worst = sorted.maxOf { it.effectiveReels }
        val allEqual = sorted.first().effectiveReels == worst
        return sorted.map { s ->
            LeaderboardRow(
                rank = 1 + sorted.count { it.effectiveReels < s.effectiveReels },
                player = s.player,
                reels = s.effectiveReels,
                isWeakest = !allEqual && s.effectiveReels == worst,
                disqualified = s.disqualified,
            )
        }
    }
}

// ----- lifecycle ---------------------------------------------------------------------------------

sealed interface SquadEvent {
    /** A member opens an invite link. [side] is in the link. */
    data class Join(val side: SquadSide, val player: PlayerId, val at: Instant) : SquadEvent

    /** The other leader opens the challenge link and creates squad B. [isPro] is that leader's plan. */
    data class ClaimSideB(val leader: PlayerId, val isPro: Boolean, val squadName: String, val at: Instant) : SquadEvent

    /** A member leaves before the roster is locked. */
    data class Leave(val side: SquadSide, val player: PlayerId, val at: Instant) : SquadEvent

    /** A leader taps ready. When both squads are ready and valid, the battle starts. */
    data class LockRoster(val side: SquadSide, val by: PlayerId, val at: Instant) : SquadEvent

    data class Cancel(val by: PlayerId, val at: Instant) : SquadEvent

    data class Expire(val at: Instant) : SquadEvent

    data class Settle(val at: Instant, val reports: Map<PlayerId, MemberReport>) : SquadEvent
}

enum class SquadReject {
    ALREADY_FINAL,
    WRONG_STATE,
    LOBBY_EXPIRED,
    ROSTER_LOCKED,
    SQUAD_FULL,
    ALREADY_IN_BATTLE,
    SIDE_NOT_CLAIMED,
    SIDE_ALREADY_CLAIMED,
    NOT_THE_LEADER,
    LEADER_CANNOT_LEAVE,
    NOT_A_MEMBER,
    ROSTER_TOO_SMALL,
    LEADER_NEEDS_PRO,
    TOO_EARLY,
}

sealed interface SquadTransition {
    data class Moved(val battle: SquadBattle) : SquadTransition
    data class Rejected(val reason: SquadReject) : SquadTransition
}

object SquadStateMachine {

    fun reduce(battle: SquadBattle, event: SquadEvent): SquadTransition {
        if (battle.state.status.isTerminal) return rejected(SquadReject.ALREADY_FINAL)
        return when (event) {
            is SquadEvent.Join -> join(battle, event)
            is SquadEvent.ClaimSideB -> claim(battle, event)
            is SquadEvent.Leave -> leave(battle, event)
            is SquadEvent.LockRoster -> lock(battle, event)
            is SquadEvent.Cancel -> cancel(battle, event)
            is SquadEvent.Expire -> expire(battle, event)
            is SquadEvent.Settle -> settle(battle, event)
        }
    }

    private fun forming(battle: SquadBattle): SquadBattleState.Forming? = battle.state as? SquadBattleState.Forming

    private fun join(b: SquadBattle, e: SquadEvent.Join): SquadTransition {
        val state = forming(b) ?: return rejected(SquadReject.WRONG_STATE)
        if (!e.at.isBefore(state.expiresAt)) return rejected(SquadReject.LOBBY_EXPIRED)
        val squad = b.squad(e.side) ?: return rejected(SquadReject.SIDE_NOT_CLAIMED)
        if (b.sideOf(e.player) != null) return rejected(SquadReject.ALREADY_IN_BATTLE)
        if (squad.rosterLocked) return rejected(SquadReject.ROSTER_LOCKED)
        if (squad.members.size >= SquadRules.MAX_MEMBERS) return rejected(SquadReject.SQUAD_FULL)
        return moved(b.withSquad(e.side, squad.copy(members = squad.members + e.player)))
    }

    private fun claim(b: SquadBattle, e: SquadEvent.ClaimSideB): SquadTransition {
        val state = forming(b) ?: return rejected(SquadReject.WRONG_STATE)
        if (!e.at.isBefore(state.expiresAt)) return rejected(SquadReject.LOBBY_EXPIRED)
        if (b.squadB != null) return rejected(SquadReject.SIDE_ALREADY_CLAIMED)
        if (b.sideOf(e.leader) != null) return rejected(SquadReject.ALREADY_IN_BATTLE)
        if (!ProGate.canCreate(ChallengeMode.SQUAD, e.isPro)) return rejected(SquadReject.LEADER_NEEDS_PRO)
        return moved(b.copy(squadB = Squad(e.squadName, e.leader, listOf(e.leader))))
    }

    private fun leave(b: SquadBattle, e: SquadEvent.Leave): SquadTransition {
        forming(b) ?: return rejected(SquadReject.WRONG_STATE)
        val squad = b.squad(e.side) ?: return rejected(SquadReject.SIDE_NOT_CLAIMED)
        if (e.player !in squad.members) return rejected(SquadReject.NOT_A_MEMBER)
        if (e.player == squad.leader) return rejected(SquadReject.LEADER_CANNOT_LEAVE) // the leader cancels instead
        if (squad.rosterLocked) return rejected(SquadReject.ROSTER_LOCKED)
        return moved(b.withSquad(e.side, squad.copy(members = squad.members - e.player)))
    }

    private fun lock(b: SquadBattle, e: SquadEvent.LockRoster): SquadTransition {
        val state = forming(b) ?: return rejected(SquadReject.WRONG_STATE)
        if (!e.at.isBefore(state.expiresAt)) return rejected(SquadReject.LOBBY_EXPIRED)
        val squad = b.squad(e.side) ?: return rejected(SquadReject.SIDE_NOT_CLAIMED)
        if (e.by != squad.leader) return rejected(SquadReject.NOT_THE_LEADER)
        if (!SquadRules.isValidRoster(squad.members.size)) return rejected(SquadReject.ROSTER_TOO_SMALL)
        val locked = b.withSquad(e.side, squad.copy(rosterLocked = true))
        val other = locked.squad(e.side.other())
        return if (other != null && other.rosterLocked) {
            // Both leaders are ready: start now.
            moved(locked.copy(state = SquadBattleState.Active(e.at, e.at.plus(b.config.duration.duration))))
        } else {
            moved(locked)
        }
    }

    private fun cancel(b: SquadBattle, e: SquadEvent.Cancel): SquadTransition {
        forming(b) ?: return rejected(SquadReject.WRONG_STATE)
        val isLeader = e.by == b.squadA.leader || e.by == b.squadB?.leader
        if (!isLeader) return rejected(SquadReject.NOT_THE_LEADER)
        return moved(b.copy(state = SquadBattleState.Cancelled(e.at, e.by, SquadCancelReason.LEADER_CANCELLED)))
    }

    private fun expire(b: SquadBattle, e: SquadEvent.Expire): SquadTransition {
        val state = forming(b) ?: return rejected(SquadReject.WRONG_STATE)
        if (e.at.isBefore(state.expiresAt)) return rejected(SquadReject.TOO_EARLY)
        return moved(b.copy(state = SquadBattleState.Expired(e.at)))
    }

    private fun settle(b: SquadBattle, e: SquadEvent.Settle): SquadTransition {
        val state = b.state as? SquadBattleState.Active ?: return rejected(SquadReject.WRONG_STATE)
        if (e.at.isBefore(state.endsAt)) return rejected(SquadReject.TOO_EARLY)
        val squadB = b.squadB ?: return rejected(SquadReject.SIDE_NOT_CLAIMED)
        val limit = b.config.reelLimitPerMember
        val a = SquadScoring.teamScore(SquadSide.A, SquadScoring.memberScores(b.squadA.members, e.reports, limit, state.startedAt, state.endsAt))
        val bScore = SquadScoring.teamScore(SquadSide.B, SquadScoring.memberScores(squadB.members, e.reports, limit, state.startedAt, state.endsAt))
        val result = SquadScoring.decide(a, bScore)
        return moved(b.copy(state = SquadBattleState.Finished(e.at, result)))
    }

    private fun SquadBattle.withSquad(side: SquadSide, squad: Squad) =
        if (side == SquadSide.A) copy(squadA = squad) else copy(squadB = squad)

    private fun moved(b: SquadBattle) = SquadTransition.Moved(b)

    private fun rejected(r: SquadReject) = SquadTransition.Rejected(r)
}
