package com.doomscrollduel.domain.usecase

enum class DuelOutcome { WIN, LOSS, DRAW }

/**
 * Who won, from one player's point of view, judged on the final reel counts only.
 * The lower count wins, even if both are over the limit; equal counts are a draw.
 *
 * Forfeits (the 10 minute rule) are decided by `DuelSettlement` in `domain.challenge.duel`; this is for
 * screens that only have the two final numbers.
 */
object ResolveDuel {
    fun forMe(myReels: Int, opponentReels: Int): DuelOutcome = when {
        myReels < opponentReels -> DuelOutcome.WIN
        myReels > opponentReels -> DuelOutcome.LOSS
        else -> DuelOutcome.DRAW
    }
}
