package com.doomscrollduel.domain.challenge.duel

import com.doomscrollduel.domain.challenge.CoinOp
import com.doomscrollduel.domain.challenge.ForfeitRule
import com.doomscrollduel.domain.challenge.PlayerId
import com.doomscrollduel.domain.challenge.TrackingGap
import java.time.Instant

sealed interface DuelEvent {
    /** The invited player taps Accept. Balances are read inside the same server transaction. */
    data class Accept(val by: PlayerId, val at: Instant, val creatorAvailable: Int, val opponentAvailable: Int) : DuelEvent

    data class Decline(val by: PlayerId, val at: Instant) : DuelEvent

    /** The creator withdraws an invite nobody has accepted yet. */
    data class Cancel(val by: PlayerId, val at: Instant) : DuelEvent

    /** Scheduled at invitedAt + 24 hours. */
    data class Expire(val at: Instant) : DuelEvent

    /**
     * A player's counter has been off for over 10 minutes. [gap] is the evidence, [at] is server time.
     * The reel counts so far are carried along so the result screen can show them.
     */
    data class TrackingOffTooLong(
        val player: PlayerId,
        val gap: TrackingGap,
        val at: Instant,
        val creatorReels: Int,
        val opponentReels: Int,
    ) : DuelEvent

    /** The time is up. Scheduled at endsAt (plus a short grace so final syncs can arrive). */
    data class Settle(val at: Instant, val creator: PlayerScore, val opponent: PlayerScore) : DuelEvent
}

enum class RejectReason {
    NOT_A_PLAYER,
    NOT_THE_OPPONENT,
    NOT_THE_CREATOR,

    /** The event does not apply in the current state, e.g. cancelling an active duel. */
    WRONG_STATE,

    /** The duel is already finished, forfeited, cancelled or expired. Repeating an event does nothing. */
    ALREADY_FINAL,

    /** Expire or Settle arrived before its time. */
    TOO_EARLY,

    /** Accept arrived after the 24 hours. The caller should send Expire. */
    INVITE_EXPIRED,

    /** The gap is not longer than 10 minutes (or is not a switched-off gap). */
    EVIDENCE_TOO_SHORT,
}

sealed interface Transition {
    /** [ops] are the coin movements to apply atomically. Their ids make a replay harmless. */
    data class Moved(val duel: Duel, val ops: List<CoinOp>) : Transition

    data class Rejected(val reason: RejectReason) : Transition
}

/**
 * The duel lifecycle. Every function is pure: it returns the next duel and the coin operations, and
 * the caller stores both in one transaction. Events on a finished duel are rejected, so settling twice
 * cannot pay twice (and the coin ids stop it even if it somehow got past this).
 */
object DuelStateMachine {

    fun reduce(duel: Duel, event: DuelEvent): Transition {
        if (duel.state.status.isTerminal) return Transition.Rejected(RejectReason.ALREADY_FINAL)
        return when (event) {
            is DuelEvent.Accept -> accept(duel, event)
            is DuelEvent.Decline -> decline(duel, event)
            is DuelEvent.Cancel -> cancel(duel, event)
            is DuelEvent.Expire -> expire(duel, event)
            is DuelEvent.TrackingOffTooLong -> forfeitNow(duel, event)
            is DuelEvent.Settle -> settle(duel, event)
        }
    }

    private fun accept(duel: Duel, e: DuelEvent.Accept): Transition {
        val state = duel.state as? DuelState.Pending ?: return reject(RejectReason.WRONG_STATE)
        if (e.by != duel.opponent) return reject(RejectReason.NOT_THE_OPPONENT)
        if (!e.at.isBefore(state.expiresAt)) return reject(RejectReason.INVITE_EXPIRED)

        val stake = duel.config.stake
        if (e.creatorAvailable < stake || e.opponentAvailable < stake) {
            // Nothing was held yet, so there is nothing to give back.
            val cancelled = duel.copy(state = DuelState.Cancelled(e.at, by = null, reason = CancelReason.INSUFFICIENT_FUNDS))
            return Transition.Moved(cancelled, emptyList())
        }
        val active = duel.copy(state = DuelState.Active(startedAt = e.at, endsAt = e.at.plus(duel.config.duration.duration)))
        return Transition.Moved(active, DuelSettlement.holdOps(duel))
    }

    private fun decline(duel: Duel, e: DuelEvent.Decline): Transition {
        if (duel.state !is DuelState.Pending) return reject(RejectReason.WRONG_STATE)
        if (e.by != duel.opponent) return reject(RejectReason.NOT_THE_OPPONENT)
        return Transition.Moved(duel.copy(state = DuelState.Cancelled(e.at, e.by, CancelReason.DECLINED)), emptyList())
    }

    private fun cancel(duel: Duel, e: DuelEvent.Cancel): Transition {
        // An accepted duel has money in escrow and two people playing. It can only end by playing out.
        if (duel.state !is DuelState.Pending) return reject(RejectReason.WRONG_STATE)
        if (e.by != duel.creator) return reject(RejectReason.NOT_THE_CREATOR)
        return Transition.Moved(duel.copy(state = DuelState.Cancelled(e.at, e.by, CancelReason.CREATOR_CANCELLED)), emptyList())
    }

    private fun expire(duel: Duel, e: DuelEvent.Expire): Transition {
        val state = duel.state as? DuelState.Pending ?: return reject(RejectReason.WRONG_STATE)
        if (e.at.isBefore(state.expiresAt)) return reject(RejectReason.TOO_EARLY)
        return Transition.Moved(duel.copy(state = DuelState.Expired(e.at)), emptyList())
    }

    private fun forfeitNow(duel: Duel, e: DuelEvent.TrackingOffTooLong): Transition {
        val state = duel.state as? DuelState.Active ?: return reject(RejectReason.WRONG_STATE)
        if (e.player !in duel.players) return reject(RejectReason.NOT_A_PLAYER)
        val until = minOf(e.at, state.endsAt)
        if (ForfeitRule.forfeitMoment(listOf(e.gap), state.startedAt, until) == null) {
            return reject(RejectReason.EVIDENCE_TOO_SHORT)
        }
        val winner = duel.other(e.player)
        fun reelsOf(player: PlayerId) = if (player == duel.creator) e.creatorReels else e.opponentReels
        val result = DuelResult.Win(
            winner = winner,
            loser = e.player,
            winnerReels = reelsOf(winner),
            loserReels = reelsOf(e.player),
            reason = WinReason.OPPONENT_FORFEIT,
        )
        return finish(duel, DuelState.Forfeited(e.at, result), result)
    }

    private fun settle(duel: Duel, e: DuelEvent.Settle): Transition {
        val state = duel.state as? DuelState.Active ?: return reject(RejectReason.WRONG_STATE)
        if (e.at.isBefore(state.endsAt)) return reject(RejectReason.TOO_EARLY)
        if (e.creator.player != duel.creator || e.opponent.player != duel.opponent) {
            return reject(RejectReason.NOT_A_PLAYER)
        }
        val result = DuelSettlement.settle(e.creator, e.opponent, state.startedAt, state.endsAt)
        val next = if (DuelSettlement.isForfeit(result)) {
            DuelState.Forfeited(e.at, result)
        } else {
            DuelState.Finished(e.at, result)
        }
        return finish(duel, next, result)
    }

    private fun finish(duel: Duel, next: DuelState, result: DuelResult): Transition {
        val updated = duel.copy(state = next)
        return Transition.Moved(updated, DuelSettlement.payoutOps(updated, result))
    }

    private fun reject(reason: RejectReason) = Transition.Rejected(reason)
}
