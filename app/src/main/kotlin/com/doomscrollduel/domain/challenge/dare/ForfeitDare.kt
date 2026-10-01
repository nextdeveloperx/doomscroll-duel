package com.doomscrollduel.domain.challenge.dare

import com.doomscrollduel.domain.challenge.PlayerId
import com.doomscrollduel.domain.challenge.duel.DuelResult
import java.time.Duration
import java.time.Instant

/**
 * FORFEIT DARE (free). A normal duel with no coin stake. When it ends, the LOSER does a dare the WINNER
 * picks from [DareCatalog] and uploads a photo or a 10 second video as proof. The winner approves or
 * rejects. The loser can skip. Either player can report.
 *
 * Time limits (each 24 hours): the winner picks, the loser sends proof, the winner reviews. If the
 * winner does not review in time the proof is approved, so a loser is never stuck.
 */
object DareRules {
    val PICK_WINDOW: Duration = Duration.ofHours(24)
    val PROOF_WINDOW: Duration = Duration.ofHours(24)
    val REVIEW_WINDOW: Duration = Duration.ofHours(24)

    /** After the first rejection the loser gets at least this long to send new proof. */
    val RESUBMIT_MIN: Duration = Duration.ofHours(6)

    /** The loser may send proof twice. A second rejection ends the dare as failed. */
    const val MAX_SUBMISSIONS = 2

    /** Proof is deleted after this long, whatever the outcome. Only the two players can ever see it. */
    val PROOF_RETENTION: Duration = Duration.ofDays(7)
}

/** A stored proof file. Private to the two players; location data is stripped before upload. */
data class ProofRef(val storagePath: String, val type: ProofType, val submittedAt: Instant)

enum class ReportReason { UNSAFE_DARE, UNSAFE_PROOF, HARASSMENT, NUDITY, OTHER }

enum class NoDareReason {
    /** Equal reel counts. */
    TIE,

    /** Both counters were off too long at the same moment. */
    MUTUAL_FORFEIT,

    /** The winner did not pick a dare within 24 hours. */
    NO_PICK,

    /** The duel itself was cancelled or expired. */
    DUEL_NOT_PLAYED,
}

enum class DareFailReason { PROOF_DEADLINE_MISSED, REJECTED_TWICE }

/**
 * `WaitingForDuel -> AwaitingPick -> AwaitingProof -> ProofSubmitted -> Completed`
 * with exits to NoDare, Failed, Skipped and Reported.
 */
sealed interface DareState {
    val isTerminal: Boolean

    /** The duel is still running. */
    data object WaitingForDuel : DareState {
        override val isTerminal get() = false
    }

    data class NoDare(val reason: NoDareReason) : DareState {
        override val isTerminal get() = true
    }

    data class AwaitingPick(val winner: PlayerId, val loser: PlayerId, val pickBy: Instant) : DareState {
        override val isTerminal get() = false
    }

    data class AwaitingProof(
        val winner: PlayerId,
        val loser: PlayerId,
        val dare: Dare,
        val dueBy: Instant,
        val submissions: Int,
    ) : DareState {
        override val isTerminal get() = false
    }

    data class ProofSubmitted(
        val winner: PlayerId,
        val loser: PlayerId,
        val dare: Dare,
        val proof: ProofRef,
        val reviewBy: Instant,
        val dueBy: Instant,
        val submissions: Int,
    ) : DareState {
        override val isTerminal get() = false
    }

    /** [autoApproved] is true when the winner did not answer in time. */
    data class Completed(val dare: Dare, val approvedAt: Instant, val autoApproved: Boolean) : DareState {
        override val isTerminal get() = true
    }

    data class Failed(val dare: Dare, val reason: DareFailReason) : DareState {
        override val isTerminal get() = true
    }

    /** The loser skipped, or the winner waived it. No penalty either way. */
    data class Skipped(val dare: Dare?, val by: PlayerId) : DareState {
        override val isTerminal get() = true
    }

    /**
     * Frozen for review. The proof is hidden from both players until a moderator decides.
     * This is final in the app; moderation happens outside it.
     */
    data class Reported(val dare: Dare?, val by: PlayerId, val reason: ReportReason, val at: Instant) : DareState {
        override val isTerminal get() = true
    }
}

sealed interface DareEvent {
    /** The duel ended. Starts the dare, or ends it with no dare. [result] is null when the duel was cancelled or expired. */
    data class DuelEnded(val result: DuelResult?, val at: Instant) : DareEvent

    data class PickDare(val by: PlayerId, val dareId: String, val at: Instant) : DareEvent
    data class SubmitProof(val by: PlayerId, val proof: ProofRef, val at: Instant) : DareEvent
    data class Approve(val by: PlayerId, val at: Instant) : DareEvent
    data class Reject(val by: PlayerId, val at: Instant) : DareEvent

    /** The loser skips the dare. */
    data class Skip(val by: PlayerId, val at: Instant) : DareEvent

    /** The winner lets the loser off. */
    data class Waive(val by: PlayerId, val at: Instant) : DareEvent

    data class Report(val by: PlayerId, val reason: ReportReason, val at: Instant) : DareEvent

    /** Time passing. Run on a schedule so deadlines fire. */
    data class Tick(val at: Instant) : DareEvent
}

enum class DareReject {
    ALREADY_FINAL,
    WRONG_STATE,
    NOT_THE_WINNER,
    NOT_THE_LOSER,
    NOT_A_PLAYER,
    NOT_IN_CATALOG,
    DEADLINE_PASSED,
    WRONG_PROOF_TYPE,
}

sealed interface DareTransition {
    data class Moved(val state: DareState) : DareTransition
    data class Rejected(val reason: DareReject) : DareTransition
}

object DareStateMachine {

    fun reduce(state: DareState, event: DareEvent): DareTransition {
        if (state.isTerminal) return rejected(DareReject.ALREADY_FINAL)
        // Time passing is always allowed and only moves the state when a deadline is reached.
        if (event is DareEvent.Tick) return DareTransition.Moved(tick(state, event.at))
        return when (state) {
            DareState.WaitingForDuel -> if (event is DareEvent.DuelEnded) duelEnded(event) else rejected(DareReject.WRONG_STATE)
            is DareState.AwaitingPick -> awaitingPick(state, event)
            is DareState.AwaitingProof -> awaitingProof(state, event)
            is DareState.ProofSubmitted -> proofSubmitted(state, event)
            else -> rejected(DareReject.ALREADY_FINAL)
        }
    }

    private fun duelEnded(e: DareEvent.DuelEnded): DareTransition {
        val state = when (val r = e.result) {
            null -> DareState.NoDare(NoDareReason.DUEL_NOT_PLAYED)
            is DuelResult.Tie -> DareState.NoDare(NoDareReason.TIE)
            DuelResult.MutualForfeit -> DareState.NoDare(NoDareReason.MUTUAL_FORFEIT)
            // A player who forfeited by switching the counter off lost, so they owe the dare like any loser.
            is DuelResult.Win -> DareState.AwaitingPick(r.winner, r.loser, pickBy = e.at.plus(DareRules.PICK_WINDOW))
        }
        return DareTransition.Moved(state)
    }

    private fun awaitingPick(s: DareState.AwaitingPick, e: DareEvent): DareTransition = when (e) {
        is DareEvent.PickDare -> when {
            e.by != s.winner -> rejected(DareReject.NOT_THE_WINNER)
            !e.at.isBefore(s.pickBy) -> rejected(DareReject.DEADLINE_PASSED)
            else -> DareCatalog.byId(e.dareId)?.let { dare ->
                DareTransition.Moved(DareState.AwaitingProof(s.winner, s.loser, dare, dueBy = e.at.plus(DareRules.PROOF_WINDOW), submissions = 0))
            } ?: rejected(DareReject.NOT_IN_CATALOG)
        }
        is DareEvent.Skip -> if (e.by == s.loser) skipped(null, e.by) else rejected(DareReject.NOT_THE_LOSER)
        is DareEvent.Waive -> if (e.by == s.winner) skipped(null, e.by) else rejected(DareReject.NOT_THE_WINNER)
        // Nothing to report yet: no dare and no proof exist.
        else -> rejected(DareReject.WRONG_STATE)
    }

    private fun awaitingProof(s: DareState.AwaitingProof, e: DareEvent): DareTransition = when (e) {
        is DareEvent.SubmitProof -> when {
            e.by != s.loser -> rejected(DareReject.NOT_THE_LOSER)
            !e.at.isBefore(s.dueBy) -> rejected(DareReject.DEADLINE_PASSED)
            e.proof.type != s.dare.proof -> rejected(DareReject.WRONG_PROOF_TYPE)
            else -> DareTransition.Moved(
                DareState.ProofSubmitted(
                    s.winner, s.loser, s.dare, e.proof,
                    reviewBy = e.at.plus(DareRules.REVIEW_WINDOW),
                    dueBy = s.dueBy,
                    submissions = s.submissions + 1,
                ),
            )
        }
        is DareEvent.Skip -> if (e.by == s.loser) skipped(s.dare, e.by) else rejected(DareReject.NOT_THE_LOSER)
        is DareEvent.Waive -> if (e.by == s.winner) skipped(s.dare, e.by) else rejected(DareReject.NOT_THE_WINNER)
        is DareEvent.Report -> report(s.winner, s.loser, s.dare, e)
        else -> rejected(DareReject.WRONG_STATE)
    }

    private fun proofSubmitted(s: DareState.ProofSubmitted, e: DareEvent): DareTransition = when (e) {
        is DareEvent.Approve ->
            if (e.by != s.winner) rejected(DareReject.NOT_THE_WINNER)
            else DareTransition.Moved(DareState.Completed(s.dare, e.at, autoApproved = false))
        is DareEvent.Reject -> when {
            e.by != s.winner -> rejected(DareReject.NOT_THE_WINNER)
            s.submissions >= DareRules.MAX_SUBMISSIONS ->
                DareTransition.Moved(DareState.Failed(s.dare, DareFailReason.REJECTED_TWICE))
            else -> DareTransition.Moved(
                DareState.AwaitingProof(
                    s.winner, s.loser, s.dare,
                    dueBy = maxOf(s.dueBy, e.at.plus(DareRules.RESUBMIT_MIN)),
                    submissions = s.submissions,
                ),
            )
        }
        is DareEvent.Waive -> if (e.by == s.winner) skipped(s.dare, e.by) else rejected(DareReject.NOT_THE_WINNER)
        is DareEvent.Report -> report(s.winner, s.loser, s.dare, e)
        // After sending proof the loser can no longer skip; the winner decides.
        else -> rejected(DareReject.WRONG_STATE)
    }

    private fun report(winner: PlayerId, loser: PlayerId, dare: Dare, e: DareEvent.Report): DareTransition =
        if (e.by != winner && e.by != loser) {
            rejected(DareReject.NOT_A_PLAYER)
        } else {
            DareTransition.Moved(DareState.Reported(dare, e.by, e.reason, e.at))
        }

    private fun tick(state: DareState, now: Instant): DareState = when (state) {
        is DareState.AwaitingPick ->
            if (!now.isBefore(state.pickBy)) DareState.NoDare(NoDareReason.NO_PICK) else state
        is DareState.AwaitingProof ->
            if (!now.isBefore(state.dueBy)) DareState.Failed(state.dare, DareFailReason.PROOF_DEADLINE_MISSED) else state
        is DareState.ProofSubmitted ->
            if (!now.isBefore(state.reviewBy)) DareState.Completed(state.dare, state.reviewBy, autoApproved = true) else state
        else -> state
    }

    private fun skipped(dare: Dare?, by: PlayerId) = DareTransition.Moved(DareState.Skipped(dare, by))

    private fun rejected(r: DareReject) = DareTransition.Rejected(r)
}
