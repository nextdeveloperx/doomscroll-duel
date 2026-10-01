package com.doomscrollduel.domain.challenge.duel

import com.doomscrollduel.domain.challenge.PlayerId
import java.time.Duration
import java.time.Instant

/** Who is ahead. The lower reel count is ahead. */
sealed interface LeadView {
    data class You(val by: Int) : LeadView
    data class Opponent(val by: Int) : LeadView
    data object Tied : LeadView
}

/**
 * Everything a screen needs to show for one duel, from one player's point of view. The UI never has
 * to look at raw state or work out who won.
 */
sealed interface DuelViewState {
    /** I was invited and can accept or decline. */
    data class InviteReceived(val expiresIn: Duration, val stake: Int, val reelLimit: Int, val duration: DuelDuration) : DuelViewState

    /** I sent the invite and am waiting. I can still cancel. */
    data class InviteSent(val expiresIn: Duration) : DuelViewState

    /** Playing. [lead] and the counts are live. */
    data class Running(
        val timeLeft: Duration,
        val myReels: Int,
        val opponentReels: Int,
        val reelLimit: Int,
        val lead: LeadView,
        /** Coins at stake for the winner (both stakes), 0 for a dare duel. */
        val pot: Int,
    ) : DuelViewState

    data class Won(val myReels: Int, val opponentReels: Int, val coinsWon: Int, val byForfeit: Boolean) : DuelViewState

    data class Lost(val myReels: Int, val opponentReels: Int, val coinsLost: Int, val byForfeit: Boolean) : DuelViewState

    /** Equal counts. Stakes were returned. */
    data class Tied(val reels: Int, val coinsReturned: Int) : DuelViewState

    /** Both counters were off too long at the same moment. Stakes were returned. */
    data class MutualForfeit(val coinsReturned: Int) : DuelViewState

    data class Cancelled(val reason: CancelReason, val byMe: Boolean) : DuelViewState

    data object Expired : DuelViewState

    /** I am not part of this duel. */
    data object NotMine : DuelViewState
}

object DuelViews {
    /**
     * [myReels] and [opponentReels] are the live counts, needed only while the duel is ACTIVE.
     * The opponent's count is shown only then, which matches the server rule that it is readable only
     * while the duel is active.
     */
    fun forViewer(
        duel: Duel,
        viewer: PlayerId,
        now: Instant,
        myReels: Int = 0,
        opponentReels: Int = 0,
    ): DuelViewState {
        if (viewer !in duel.players) return DuelViewState.NotMine
        val stake = duel.config.stake
        return when (val state = duel.state) {
            is DuelState.Pending -> {
                val left = Duration.between(now, state.expiresAt).coerceAtLeast(Duration.ZERO)
                if (viewer == duel.opponent) {
                    DuelViewState.InviteReceived(left, stake, duel.config.reelLimit, duel.config.duration)
                } else {
                    DuelViewState.InviteSent(left)
                }
            }
            is DuelState.Active -> DuelViewState.Running(
                timeLeft = Duration.between(now, state.endsAt).coerceAtLeast(Duration.ZERO),
                myReels = myReels,
                opponentReels = opponentReels,
                reelLimit = duel.config.reelLimit,
                lead = lead(myReels, opponentReels),
                pot = stake * 2,
            )
            is DuelState.Finished -> ended(state.result, viewer, stake)
            is DuelState.Forfeited -> ended(state.result, viewer, stake)
            is DuelState.Cancelled -> DuelViewState.Cancelled(state.reason, byMe = state.by == viewer)
            is DuelState.Expired -> DuelViewState.Expired
        }
    }

    fun lead(myReels: Int, opponentReels: Int): LeadView = when {
        myReels < opponentReels -> LeadView.You(opponentReels - myReels)
        myReels > opponentReels -> LeadView.Opponent(myReels - opponentReels)
        else -> LeadView.Tied
    }

    private fun ended(result: DuelResult, viewer: PlayerId, stake: Int): DuelViewState = when (result) {
        is DuelResult.Win -> {
            val forfeit = result.reason == WinReason.OPPONENT_FORFEIT
            if (result.winner == viewer) {
                DuelViewState.Won(result.winnerReels, result.loserReels, coinsWon = stake, byForfeit = forfeit)
            } else {
                DuelViewState.Lost(result.loserReels, result.winnerReels, coinsLost = stake, byForfeit = forfeit)
            }
        }
        is DuelResult.Tie -> DuelViewState.Tied(result.reels, coinsReturned = stake)
        DuelResult.MutualForfeit -> DuelViewState.MutualForfeit(coinsReturned = stake)
    }
}
