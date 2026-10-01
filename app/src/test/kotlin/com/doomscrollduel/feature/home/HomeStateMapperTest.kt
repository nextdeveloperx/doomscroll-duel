package com.doomscrollduel.feature.home

import com.doomscrollduel.core.designsystem.brain.BrainState
import com.doomscrollduel.domain.model.DailyReelStats
import com.doomscrollduel.tracking.health.TrackingHealth
import com.doomscrollduel.tracking.health.TrackingIssue
import com.doomscrollduel.tracking.model.TrackedApp
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeStateMapperTest {
    private val date = LocalDate.of(2026, 10, 1)
    private val profile = Profile("Rohan", 1250)
    private val ok = TrackingHealth(accessibilityEnabled = true, batteryUnrestricted = true)

    private fun stats(ig: Int = 0, yt: Int = 0, fb: Int = 0, snap: Int = 0) = DailyReelStats(
        date,
        mapOf(TrackedApp.INSTAGRAM to ig, TrackedApp.YOUTUBE to yt, TrackedApp.FACEBOOK to fb, TrackedApp.SNAPCHAT to snap),
        updatedAtMillis = 1L,
    )

    private fun map(stats: DailyReelStats, limit: Int = 100, health: TrackingHealth = ok, battle: ActiveBattleUi? = null) =
        HomeStateMapper.map(stats, limit, streak = 3, health = health, profile = profile, battle = battle)

    @Test
    fun `total is the sum of all apps and the breakdown keeps app order`() {
        val state = map(stats(ig = 21, yt = 17, fb = 6, snap = 3))
        assertEquals(47, state.reelsToday)
        assertEquals(TrackedApp.entries, state.perApp.map { it.app })
        assertEquals(listOf(21, 17, 6, 3), state.perApp.map { it.count })
        assertEquals(3, state.streakDays)
    }

    @Test
    fun `brain state and hp follow the percent of the limit`() {
        assertEquals(BrainState.HAPPY, map(stats(ig = 40)).brainState)
        assertEquals(60, map(stats(ig = 40)).hp)
        assertEquals(BrainState.FRIED, map(stats(ig = 41)).brainState)
        assertEquals(BrainState.FRIED, map(stats(ig = 99)).brainState)
        assertEquals(BrainState.ZOMBIE, map(stats(ig = 60, yt = 40)).brainState)
        assertEquals(0, map(stats(ig = 150)).hp)
        assertEquals(BrainState.FRIED, map(stats(ig = 47)).brainState)
        assertEquals(53, map(stats(ig = 47)).hp)
    }

    @Test
    fun `a changed limit changes the brain`() {
        assertEquals(BrainState.ZOMBIE, map(stats(ig = 47), limit = 40).brainState)
        assertEquals(BrainState.HAPPY, map(stats(ig = 47), limit = 200).brainState)
    }

    @Test
    fun `the banner follows tracking health`() {
        assertEquals(TrackingIssue.NONE, map(stats()).issue)
        assertEquals(TrackingIssue.ACCESSIBILITY_OFF, map(stats(), health = TrackingHealth(false, true)).issue)
        assertEquals(TrackingIssue.BATTERY_RESTRICTED, map(stats(), health = TrackingHealth(true, false)).issue)
    }

    @Test
    fun `the duel card shows the real count for me`() {
        val battle = ActiveBattleUi("Aman", myReels = 0, opponentReels = 31, timeLeft = "3h", stakeCoins = 50)
        val state = map(stats(ig = 12, yt = 5), battle = battle)
        assertEquals(17, state.battle?.myReels)
        assertEquals(31, state.battle?.opponentReels)
        assertNull(map(stats()).battle)
    }
}
