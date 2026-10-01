package com.doomscrollduel.feature.home

import com.doomscrollduel.domain.blocking.ActiveWindow
import com.doomscrollduel.domain.model.DailyReelStats
import com.doomscrollduel.tracking.health.TrackingHealth
import com.doomscrollduel.tracking.model.TrackedApp

/** Name and coins come from the profile and wallet, which are not built yet (see FakeProfile). */
data class Profile(val name: String, val coins: Int)

object FakeProfile {
    val current = Profile(name = "Rohan", coins = 1250)
}

/** Builds the Home screen state from real counts. Pure, so it is unit-tested. */
object HomeStateMapper {
    fun map(
        stats: DailyReelStats,
        limit: Int,
        streak: Int,
        health: TrackingHealth,
        profile: Profile,
        battle: ActiveBattleUi?,
        window: ActiveWindow? = null,
    ): HomeUiState = HomeUiState(
        userName = profile.name,
        streakDays = streak,
        coins = profile.coins,
        reelsToday = stats.total,
        reelLimit = limit,
        perApp = TrackedApp.entries.map { AppCount(it, stats.perApp[it] ?: 0) },
        issue = health.issue,
        // A duel's own count starts at its start time; until duels are real this mirrors today's total.
        battle = battle?.copy(myReels = stats.total),
        window = window,
    )
}
