package com.doomscrollduel.feature.duel.live

/** Warn the user when this few reels are left before the limit. */
const val WarningThreshold = 5

data class LiveFighter(val name: String, val reels: Int)

data class LiveDuelUiState(
    val me: LiveFighter,
    val opponent: LiveFighter,
    val reelLimit: Int,
    val timeLeft: String,
    val stakeCoins: Int,
) {
    val reelsLeft: Int get() = reelLimit - me.reels

    val warning: LiveWarning
        get() = when {
            reelsLeft <= 0 -> LiveWarning.LimitReached
            reelsLeft <= WarningThreshold -> LiveWarning.Close(reelsLeft)
            else -> LiveWarning.None
        }

    /** Lower reel count is ahead. */
    val lead: Lead
        get() = when {
            me.reels < opponent.reels -> Lead.You(opponent.reels - me.reels)
            me.reels > opponent.reels -> Lead.Opponent(me.reels - opponent.reels)
            else -> Lead.Tied
        }
}

sealed interface LiveWarning {
    data object None : LiveWarning
    data class Close(val reelsLeft: Int) : LiveWarning
    data object LimitReached : LiveWarning
}

sealed interface Lead {
    data class You(val by: Int) : Lead
    data class Opponent(val by: Int) : Lead
    data object Tied : Lead
}
