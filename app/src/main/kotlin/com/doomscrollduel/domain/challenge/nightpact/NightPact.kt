package com.doomscrollduel.domain.challenge.nightpact

import com.doomscrollduel.domain.challenge.ChallengeId
import com.doomscrollduel.domain.challenge.ForfeitRule
import com.doomscrollduel.domain.challenge.PlayerId
import com.doomscrollduel.domain.challenge.TrackingGap
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * NIGHT PACT (free). Two or more friends agree on zero reels between 11 PM and 6 AM, each in their own
 * local time. Any counted reel in that window breaks the night for that person. The pact streak is the
 * number of nights in a row that EVERYONE kept it. No coins.
 *
 * A "night" is named after the evening it starts on: the night of 1 October runs from 23:00 on the 1st
 * to 06:00 on the 2nd.
 */
object NightWindow {
    val START: LocalTime = LocalTime.of(23, 0)
    val END: LocalTime = LocalTime.of(6, 0)

    /** The window for the night that starts on [night], in [zone]. Daylight saving is handled by the zone. */
    fun of(night: LocalDate, zone: ZoneId): Pair<Instant, Instant> = Pair(
        night.atTime(START).atZone(zone).toInstant(),
        night.plusDays(1).atTime(END).atZone(zone).toInstant(),
    )

    /** Which night [instant] belongs to, or null in the daytime (06:00 to 23:00 local). */
    fun nightOf(instant: Instant, zone: ZoneId): LocalDate? {
        val local = instant.atZone(zone)
        val time = local.toLocalTime()
        return when {
            time >= START -> local.toLocalDate()
            time < END -> local.toLocalDate().minusDays(1)
            else -> null
        }
    }
}

sealed interface MemberStatus {
    data object Invited : MemberStatus

    /** Takes part from the first night whose window starts after [joinedAt]. */
    data class Accepted(val joinedAt: Instant) : MemberStatus

    data class Left(val at: Instant) : MemberStatus
}

data class PactMember(val player: PlayerId, val zone: ZoneId, val status: MemberStatus)

enum class PactEndReason { TOO_FEW_MEMBERS, ENDED_BY_CREATOR }

/** `Pending -> Active -> Ended`, or `Pending -> Cancelled | Expired`. */
sealed interface PactState {
    data class Pending(val invitedAt: Instant, val expiresAt: Instant) : PactState
    /** [inviteExpiresAt] is when invites that nobody answered yet stop working. */
    data class Active(val startedAt: Instant, val inviteExpiresAt: Instant) : PactState
    data class Ended(val at: Instant, val reason: PactEndReason) : PactState
    data class Cancelled(val at: Instant) : PactState
    data class Expired(val at: Instant) : PactState

    val isTerminal: Boolean get() = this !is Pending && this !is Active
}

/** What happened to one member on one night. */
sealed interface NightVerdict {
    data object Kept : NightVerdict

    /** At least one reel was counted in the window. */
    data class Broken(val reels: Int) : NightVerdict

    /**
     * We cannot prove they kept it: no report arrived, or their counter was off for over 10 minutes inside
     * the window. This breaks the night too, because switching the counter off would otherwise be a free pass.
     */
    data class Unverified(val reason: UnverifiedReason) : NightVerdict

    /** Not in the pact that night (not joined yet, or already left). Ignored. */
    data object NotParticipating : NightVerdict
}

enum class UnverifiedReason { NO_REPORT, COUNTER_OFF }

/** What one phone reports for one night. It holds a number, never the time of any reel. */
data class NightReport(val reelsInWindow: Int, val gaps: List<TrackingGap> = emptyList())

data class NightRecord(val night: LocalDate, val verdicts: Map<PlayerId, NightVerdict>) {
    private val participants get() = verdicts.values.filter { it != NightVerdict.NotParticipating }

    /** Everyone who took part kept it, and at least two people did. */
    val everyoneKept: Boolean get() = participants.size >= 2 && participants.all { it == NightVerdict.Kept }

    /** The people who broke or could not prove it. */
    val breakers: List<PlayerId>
        get() = verdicts.filterValues { it is NightVerdict.Broken || it is NightVerdict.Unverified }.keys.toList()
}

data class PactStreak(val current: Int, val best: Int)

data class NightPact(
    val id: ChallengeId,
    val creator: PlayerId,
    val members: List<PactMember>,
    val state: PactState,
    val nights: List<NightRecord> = emptyList(),
) {
    init {
        require(members.any { it.player == creator }) { "the creator must be a member" }
        require(members.map { it.player }.distinct().size == members.size) { "duplicate members" }
    }

    fun member(player: PlayerId) = members.firstOrNull { it.player == player }

    val activeMembers get() = members.filter { it.status is MemberStatus.Accepted }

    companion object {
        /** Two or more friends. The creator counts as accepted from the start. */
        fun create(
            id: ChallengeId,
            creator: PlayerId,
            creatorZone: ZoneId,
            invited: List<Pair<PlayerId, ZoneId>>,
            now: Instant,
        ): NightPact? {
            if (invited.isEmpty() || invited.any { it.first == creator }) return null
            if (invited.map { it.first }.distinct().size != invited.size) return null
            val members = listOf(PactMember(creator, creatorZone, MemberStatus.Accepted(now))) +
                invited.map { (p, z) -> PactMember(p, z, MemberStatus.Invited) }
            return NightPact(id, creator, members, PactState.Pending(now, now.plus(Duration.ofHours(24))))
        }
    }
}

object NightPactRules {

    /** Does [member] take part in the night starting on [night]? */
    fun participates(member: PactMember, night: LocalDate): Boolean {
        val windowStart = NightWindow.of(night, member.zone).first
        return when (val s = member.status) {
            is MemberStatus.Accepted -> s.joinedAt.isBefore(windowStart)
            is MemberStatus.Left -> false
            MemberStatus.Invited -> false
        }
    }

    fun verdict(report: NightReport?, window: Pair<Instant, Instant>): NightVerdict = when {
        report == null -> NightVerdict.Unverified(UnverifiedReason.NO_REPORT)
        report.reelsInWindow > 0 -> NightVerdict.Broken(report.reelsInWindow)
        ForfeitRule.forfeitMoment(report.gaps, window.first, window.second) != null ->
            NightVerdict.Unverified(UnverifiedReason.COUNTER_OFF)
        else -> NightVerdict.Kept
    }

    /** Builds the record for [night]. Run once every member has reported, or after the report deadline. */
    fun evaluateNight(pact: NightPact, night: LocalDate, reports: Map<PlayerId, NightReport>): NightRecord {
        val verdicts = pact.members.associate { m ->
            m.player to if (participates(m, night)) {
                verdict(reports[m.player], NightWindow.of(night, m.zone))
            } else {
                NightVerdict.NotParticipating
            }
        }
        return NightRecord(night, verdicts)
    }

    /** Adds the night to the pact and ends the pact when fewer than two people are still in it. */
    fun recordNight(pact: NightPact, record: NightRecord, at: Instant): NightPact {
        if (pact.state !is PactState.Active) return pact
        if (pact.nights.any { it.night == record.night }) return pact // a night is recorded once
        val withNight = pact.copy(nights = (pact.nights + record).sortedBy { it.night })
        return endIfTooFew(withNight, at)
    }

    /**
     * Nights in a row that everyone kept it, counting back from the most recent recorded night.
     * One night with a breaker, or a missing night in between, resets it. [best] is the longest run ever.
     */
    fun streak(nights: List<NightRecord>): PactStreak {
        if (nights.isEmpty()) return PactStreak(0, 0)
        val sorted = nights.sortedBy { it.night }
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        for (record in sorted) {
            val consecutive = previous != null && previous.plusDays(1) == record.night
            run = when {
                !record.everyoneKept -> 0
                consecutive -> run + 1
                else -> 1
            }
            best = maxOf(best, run)
            previous = record.night
        }
        return PactStreak(current = run, best = best)
    }

    fun endIfTooFew(pact: NightPact, at: Instant): NightPact =
        if (pact.state is PactState.Active && pact.activeMembers.size < 2) {
            pact.copy(state = PactState.Ended(at, PactEndReason.TOO_FEW_MEMBERS))
        } else {
            pact
        }
}

// ----- lifecycle ---------------------------------------------------------------------------------

sealed interface PactEvent {
    data class Accept(val player: PlayerId, val at: Instant) : PactEvent
    data class Decline(val player: PlayerId, val at: Instant) : PactEvent
    data class Leave(val player: PlayerId, val at: Instant) : PactEvent
    data class Cancel(val by: PlayerId, val at: Instant) : PactEvent
    data class Expire(val at: Instant) : PactEvent
}

enum class PactReject { ALREADY_FINAL, WRONG_STATE, NOT_INVITED, NOT_A_MEMBER, NOT_THE_CREATOR, TOO_EARLY, INVITE_EXPIRED }

sealed interface PactTransition {
    data class Moved(val pact: NightPact) : PactTransition
    data class Rejected(val reason: PactReject) : PactTransition
}

object NightPactMachine {
    fun reduce(pact: NightPact, event: PactEvent): PactTransition {
        if (pact.state.isTerminal) return PactTransition.Rejected(PactReject.ALREADY_FINAL)
        return when (event) {
            is PactEvent.Accept -> accept(pact, event)
            is PactEvent.Decline -> decline(pact, event)
            is PactEvent.Leave -> leave(pact, event)
            is PactEvent.Cancel -> cancel(pact, event)
            is PactEvent.Expire -> expire(pact, event)
        }
    }

    /** Invites stay open for 24 hours, also after the pact has started with the first yes. */
    private fun inviteExpiry(p: NightPact): Instant? = when (val s = p.state) {
        is PactState.Pending -> s.expiresAt
        is PactState.Active -> s.inviteExpiresAt
        else -> null
    }

    private fun accept(p: NightPact, e: PactEvent.Accept): PactTransition {
        val expiresAt = inviteExpiry(p) ?: return reject(PactReject.WRONG_STATE)
        val m = p.member(e.player) ?: return reject(PactReject.NOT_A_MEMBER)
        if (m.status != MemberStatus.Invited) return reject(PactReject.NOT_INVITED)
        if (!e.at.isBefore(expiresAt)) return reject(PactReject.INVITE_EXPIRED)
        val updated = p.replace(m.copy(status = MemberStatus.Accepted(e.at)))
        // The pact starts with the first yes. Anyone who accepts later joins from the next night whose
        // window has not started yet (see `participates`).
        val next = if (p.state is PactState.Pending) PactState.Active(startedAt = e.at, inviteExpiresAt = expiresAt) else p.state
        return PactTransition.Moved(updated.copy(state = next))
    }

    private fun decline(p: NightPact, e: PactEvent.Decline): PactTransition {
        if (inviteExpiry(p) == null) return reject(PactReject.WRONG_STATE)
        val m = p.member(e.player) ?: return reject(PactReject.NOT_A_MEMBER)
        if (m.status != MemberStatus.Invited) return reject(PactReject.NOT_INVITED)
        val updated = p.replace(m.copy(status = MemberStatus.Left(e.at)))
        // A pact nobody accepted, and nobody can still accept, is cancelled.
        val nobodyLeftToAsk = updated.members.none { it.status == MemberStatus.Invited }
        return if (p.state is PactState.Pending && nobodyLeftToAsk) {
            PactTransition.Moved(updated.copy(state = PactState.Cancelled(e.at)))
        } else {
            PactTransition.Moved(NightPactRules.endIfTooFew(updated, e.at))
        }
    }

    private fun leave(p: NightPact, e: PactEvent.Leave): PactTransition {
        if (p.state !is PactState.Active) return reject(PactReject.WRONG_STATE)
        val m = p.member(e.player) ?: return reject(PactReject.NOT_A_MEMBER)
        if (m.status !is MemberStatus.Accepted) return reject(PactReject.NOT_A_MEMBER)
        val updated = p.replace(m.copy(status = MemberStatus.Left(e.at)))
        return PactTransition.Moved(NightPactRules.endIfTooFew(updated, e.at))
    }

    private fun cancel(p: NightPact, e: PactEvent.Cancel): PactTransition {
        if (e.by != p.creator) return reject(PactReject.NOT_THE_CREATOR)
        return when (p.state) {
            is PactState.Pending -> PactTransition.Moved(p.copy(state = PactState.Cancelled(e.at)))
            is PactState.Active -> PactTransition.Moved(p.copy(state = PactState.Ended(e.at, PactEndReason.ENDED_BY_CREATOR)))
            else -> reject(PactReject.WRONG_STATE)
        }
    }

    private fun expire(p: NightPact, e: PactEvent.Expire): PactTransition {
        val state = p.state as? PactState.Pending ?: return reject(PactReject.WRONG_STATE)
        if (e.at.isBefore(state.expiresAt)) return reject(PactReject.TOO_EARLY)
        return PactTransition.Moved(p.copy(state = PactState.Expired(e.at)))
    }

    private fun NightPact.replace(member: PactMember) =
        copy(members = members.map { if (it.player == member.player) member else it })

    private fun reject(r: PactReject) = PactTransition.Rejected(r)
}
