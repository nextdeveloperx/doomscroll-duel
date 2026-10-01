package com.doomscrollduel.feature

import com.doomscrollduel.feature.duel.create.DurationOption
import com.doomscrollduel.feature.duel.create.NewDuelUiState
import com.doomscrollduel.feature.duel.live.LiveDuelUiState
import com.doomscrollduel.feature.duel.live.LiveFighter
import com.doomscrollduel.feature.duel.result.ResultFighter
import com.doomscrollduel.feature.duel.result.ResultUiState
import com.doomscrollduel.feature.home.ActiveBattleUi
import com.doomscrollduel.feature.home.HomeUiState
import com.doomscrollduel.feature.settings.SettingsUiState

/**
 * Stand-in data for the first UI pass and for previews. The Room-backed repositories replace this
 * (milestones 3 and 6); screens only ever see the UiState classes, so nothing else changes.
 */
object FakeData {
    val home = HomeUiState(
        userName = "Rohan",
        streakDays = 12,
        coins = 1250,
        reelsToday = 47,
        reelLimit = 100,
        battle = ActiveBattleUi(
            opponentName = "Aman",
            myReels = 47,
            opponentReels = 31,
            timeLeft = "3h 20m",
            stakeCoins = 50,
        ),
    )

    val newDuel = NewDuelUiState(
        friends = listOf("Aman", "Riya", "Kabir", "Sneha", "Dev"),
        selectedFriendIndex = 0,
        reelLimit = 100,
        duration = DurationOption.ONE_DAY,
        stakeCoins = 50,
    )

    /** Four reels from the limit, so the warning card shows. */
    val live = LiveDuelUiState(
        me = LiveFighter("Rohan", 96),
        opponent = LiveFighter("Aman", 58),
        reelLimit = 100,
        timeLeft = "02:14:36",
        stakeCoins = 50,
    )

    val result = ResultUiState(
        me = ResultFighter("Rohan", 104),
        opponent = ResultFighter("Aman", 38),
        reelLimit = 100,
        stakeCoins = 50,
    )

    val settings = SettingsUiState(
        strictLock = true,
        friendUnlock = false,
        wait10Gate = true,
        bedtimeMode = true,
        focusHours = false,
        accessibilityEnabled = true,
        batteryUnrestricted = false,
    )
}
