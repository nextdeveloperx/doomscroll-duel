package com.doomscrollduel.domain.challenge.duel

import com.doomscrollduel.domain.challenge.ChallengeId
import com.doomscrollduel.domain.challenge.ChallengeMode
import com.doomscrollduel.domain.challenge.DuelStatus
import com.doomscrollduel.domain.challenge.PlayerId
import java.time.Duration
import java.time.Instant

enum class DuelDuration(val duration: Duration) {
    SIX_HOURS(Duration.ofHours(6)),
    ONE_DAY(Duration.ofHours(24)),
    SEVEN_DAYS(Duration.ofDays(7)),
}

/**
 * Rules the players agree on when the invite is sent.
 * [stake] is virtual coins held in escrow once the invite is accepted. A DUEL needs a stake of at
 * least [MIN_STAKE]; a FORFEIT_DARE has none (the dare is the stake).
 */
data class DuelConfig(
    val reelLimit: Int,
    val duration: DuelDuration,
    val stake: Int,
) {
    init {
        require(reelLimit in MIN_LIMIT..MAX_LIMIT) { "reelLimit must be $MIN_LIMIT..$MAX_LIMIT" }
        require(stake in 0..MAX_STAKE) { "stake must be 0..$MAX_STAKE" }
    }

    companion object {
        const val MIN_LIMIT = 10
        const val MAX_LIMIT = 500
        const val MIN_STAKE = 1
        const val MAX_STAKE = 1_000
    }
}

enum class WinReason {
    /** Played to the end; the lower reel count won. */
    LOWER_COUNT,

    /** The other player's accessibility service stayed off for more than 10 minutes. */
    OPPONENT_FORFEIT,
}

sealed interface DuelResult {
    data class Win(
        val winner: PlayerId,
        val loser: PlayerId,
        val winnerReels: Int,
        val loserReels: Int,
        val reason: WinReason,
    ) : DuelResult

    /** Equal counts. Both stakes go back. */
    data class Tie(val reels: Int) : DuelResult

    /** Both players crossed the 10 minute rule at the same instant. Both stakes go back. */
    data object MutualForfeit : DuelResult
}

enum class CancelReason {
    DECLINED,
    CREATOR_CANCELLED,

    /** Someone could not cover the stake when the invite was accepted. Nothing was held. */
    INSUFFICIENT_FUNDS,
}

/** `PENDING -> ACTIVE -> FINISHED | FORFEITED`, or `PENDING -> CANCELLED | EXPIRED`. */
sealed interface DuelState {
    val status: DuelStatus

    /** Invite sent. Expires 24 hours after [invitedAt]. No coins are held yet. */
    data class Pending(val invitedAt: Instant, val expiresAt: Instant) : DuelState {
        override val status get() = DuelStatus.PENDING
    }

    /** Accepted. Stakes are in escrow and counting runs from [startedAt] to [endsAt]. */
    data class Active(val startedAt: Instant, val endsAt: Instant) : DuelState {
        override val status get() = DuelStatus.ACTIVE
    }

    /** Played to the end: a win by lower count, or a tie. */
    data class Finished(val settledAt: Instant, val result: DuelResult) : DuelState {
        override val status get() = DuelStatus.FINISHED
    }

    /** Ended early or at settlement by the 10 minute rule. */
    data class Forfeited(val settledAt: Instant, val result: DuelResult) : DuelState {
        override val status get() = DuelStatus.FORFEITED
    }

    /** [by] is null when the system cancelled it (insufficient funds). */
    data class Cancelled(val at: Instant, val by: PlayerId?, val reason: CancelReason) : DuelState {
        override val status get() = DuelStatus.CANCELLED
    }

    /** Nobody answered within 24 hours. */
    data class Expired(val at: Instant) : DuelState {
        override val status get() = DuelStatus.EXPIRED
    }
}

/** Used by [ChallengeMode.DUEL] and [ChallengeMode.FORFEIT_DARE]. */
data class Duel(
    val id: ChallengeId,
    val mode: ChallengeMode,
    val creator: PlayerId,
    val opponent: PlayerId,
    val config: DuelConfig,
    val state: DuelState,
) {
    init {
        require(mode == ChallengeMode.DUEL || mode == ChallengeMode.FORFEIT_DARE) { "not a two-player duel mode" }
        require(creator != opponent) { "cannot duel yourself" }
        if (mode == ChallengeMode.DUEL) require(config.stake >= DuelConfig.MIN_STAKE) { "a duel needs a stake" }
        if (mode == ChallengeMode.FORFEIT_DARE) require(config.stake == 0) { "a dare duel has no coin stake" }
    }

    val players: Set<PlayerId> get() = setOf(creator, opponent)

    fun other(player: PlayerId): PlayerId = if (player == creator) opponent else creator

    companion object {
        /** A new invite. Expires 24 hours after [now]. */
        fun invite(
            id: ChallengeId,
            mode: ChallengeMode,
            creator: PlayerId,
            opponent: PlayerId,
            config: DuelConfig,
            now: Instant,
        ): Duel = Duel(
            id, mode, creator, opponent, config,
            DuelState.Pending(invitedAt = now, expiresAt = now.plus(Duration.ofHours(24))),
        )
    }
}
