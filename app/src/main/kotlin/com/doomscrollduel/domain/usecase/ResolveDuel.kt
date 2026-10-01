package com.doomscrollduel.domain.usecase

enum class DuelOutcome { WIN, LOSS, DRAW }

/**
 * Who wins a duel, from one player's point of view. Pure Kotlin.
 *
 * - Reaching the reel limit (reels >= limit) forfeits the stake, unless the other player did too.
 * - Otherwise the lower reel count wins; equal counts are a draw and coins go back.
 *
 * The real result is settled by a Cloud Function; this mirrors it so offline screens agree.
 */
object ResolveDuel {
    fun forMe(myReels: Int, opponentReels: Int, limit: Int): DuelOutcome {
        val iReachedLimit = myReels >= limit
        val theyReachedLimit = opponentReels >= limit
        if (iReachedLimit != theyReachedLimit) {
            return if (iReachedLimit) DuelOutcome.LOSS else DuelOutcome.WIN
        }
        return when {
            myReels < opponentReels -> DuelOutcome.WIN
            myReels > opponentReels -> DuelOutcome.LOSS
            else -> DuelOutcome.DRAW
        }
    }
}
