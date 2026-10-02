package com.doomscrollduel.feature.home

import com.doomscrollduel.core.designsystem.brain.BrainState
import com.doomscrollduel.core.designsystem.brain.ReelUsage
import com.doomscrollduel.domain.blocking.ActiveWindow
import com.doomscrollduel.tracking.health.TrackingIssue
import com.doomscrollduel.tracking.model.TrackedApp

data class AppCount(val app: TrackedApp, val count: Int)

data class ActiveBattleUi(
    val opponentName: String,
    val myReels: Int,
    val opponentReels: Int,
    val timeLeft: String,
    val stakeCoins: Int,
    val duelId: String = "",
    /** I challenged them and they have not answered yet. */
    val waiting: Boolean = false,
    /** The battle is over and its result has not been opened yet. */
    val finished: Boolean = false,
)

data class HomeUiState(
    val userName: String,
    val streakDays: Int,
    val coins: Int,
    val reelsToday: Int,
    val reelLimit: Int,
    /** Reels per app today, in the order the apps are listed on screen. */
    val perApp: List<AppCount>,
    /** What, if anything, is stopping the counter. Drives the banner. */
    val issue: TrackingIssue,
    /** Null when no duel is running. */
    val battle: ActiveBattleUi?,
    /** A bedtime or focus window running now: the reel limit is zero until it ends. Null otherwise. */
    val window: ActiveWindow? = null,
) {
    val percentUsed: Int get() = ReelUsage.percentUsed(reelsToday, reelLimit)
    val brainState: BrainState get() = BrainState.fromPercentUsed(percentUsed)
    val hp: Int get() = ReelUsage.hpForPercentUsed(percentUsed)
}
