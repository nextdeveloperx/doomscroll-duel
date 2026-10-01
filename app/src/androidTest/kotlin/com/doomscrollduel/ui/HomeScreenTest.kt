package com.doomscrollduel.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.doomscrollduel.R
import com.doomscrollduel.feature.FakeData
import com.doomscrollduel.feature.home.HomeScreen
import com.doomscrollduel.feature.home.HomeUiState
import com.doomscrollduel.feature.legal.UsageDataCard
import com.doomscrollduel.tracking.health.TrackingIssue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule val rule = createComposeRule()

    private var newBattle = 0
    private var openBattle = 0
    private val fixed = mutableListOf<TrackingIssue>()

    private fun show(state: HomeUiState, extraTop: (@androidx.compose.runtime.Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit)? = null) {
        rule.setDuelContent {
            HomeScreen(
                state = state,
                onNewBattle = { newBattle++ },
                onOpenBattle = { openBattle++ },
                onFixTracking = { fixed += it },
                extraTop = extraTop,
            )
        }
    }

    @Test fun showsTodaysCountLimitAndHp() {
        show(FakeData.home) // 47 of 100 reels
        rule.onNodeWithText("47").assertIsDisplayed()
        rule.onNodeWithText(str(R.string.home_reels_label)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.home_limit_caption, 100)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.home_hp_value, 53)).assertIsDisplayed() // hp = 100 - 47 percent
    }

    @Test fun hpFollowsTheBrainState() {
        show(FakeData.home.copy(reelsToday = 150, reelLimit = 100)) // zombie: hp 0
        rule.onNodeWithText(str(R.string.home_hp_value, 0)).assertIsDisplayed()
    }

    @Test fun listsEveryAppWithItsNumber() {
        show(FakeData.home)
        FakeData.home.perApp.forEach {
            val label = when (it.app.name) {
                "INSTAGRAM" -> R.string.app_instagram
                "YOUTUBE" -> R.string.app_youtube
                "FACEBOOK" -> R.string.app_facebook
                else -> R.string.app_snapchat
            }
            rule.onNodeWithText(str(R.string.home_breakdown_row, str(label), it.count)).performScrollTo().assertExists()
        }
    }

    @Test fun newBattleButtonCallsBack() {
        show(FakeData.home)
        rule.onNodeWithText(str(R.string.home_new_battle)).performClick()
        assertEquals(1, newBattle)
    }

    @Test fun noBannerWhenCountingWorks() {
        show(FakeData.home.copy(issue = TrackingIssue.NONE))
        rule.onNodeWithText(str(R.string.banner_off_title)).assertDoesNotExist()
        rule.onNodeWithText(str(R.string.banner_consent_title)).assertDoesNotExist()
        rule.onNodeWithText(str(R.string.banner_battery_title)).assertDoesNotExist()
    }

    @Test fun counterOffBannerOffersAFix() {
        show(FakeData.home.copy(issue = TrackingIssue.ACCESSIBILITY_OFF))
        rule.onNodeWithText(str(R.string.banner_off_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.banner_off_action)).performClick()
        assertEquals(listOf(TrackingIssue.ACCESSIBILITY_OFF), fixed)
    }

    @Test fun missingConsentShowsTheReadFirstBanner() {
        show(FakeData.home.copy(issue = TrackingIssue.CONSENT_NEEDED))
        rule.onNodeWithText(str(R.string.banner_consent_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.banner_off_title)).assertDoesNotExist()
        rule.onNodeWithText(str(R.string.banner_consent_action)).performClick()
        assertEquals(listOf(TrackingIssue.CONSENT_NEEDED), fixed)
    }

    @Test fun batteryBannerIsAWarningNotACounterOffMessage() {
        show(FakeData.home.copy(issue = TrackingIssue.BATTERY_RESTRICTED))
        rule.onNodeWithText(str(R.string.banner_battery_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.banner_off_title)).assertDoesNotExist()
    }

    @Test fun battleCardOpensTheLiveDuel() {
        show(FakeData.home)
        rule.onNodeWithText(str(R.string.home_battle_title)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(str(R.string.home_battle_title)).performClick()
        assertTrue("clicking the battle card should open it", openBattle >= 1)
    }

    @Test fun noBattleCardWithoutABattle() {
        show(FakeData.home.copy(battle = null))
        rule.onNodeWithText(str(R.string.home_battle_title)).assertDoesNotExist()
        assertEquals(0, openBattle)
    }

    @Test fun theAnonymousDataQuestionCanBeShownAndAnswered() {
        var allowed = 0
        var declined = 0
        show(FakeData.home) { UsageDataCard(onAllow = { allowed++ }, onDecline = { declined++ }) }
        rule.onNodeWithText(str(R.string.usage_card_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.usage_card_decline)).performClick()
        rule.onNodeWithText(str(R.string.usage_card_allow)).performClick()
        assertEquals(1, declined)
        assertEquals(1, allowed)
    }

    @Test fun noQuestionCardByDefault() {
        show(FakeData.home)
        rule.onNodeWithText(str(R.string.usage_card_title)).assertDoesNotExist()
        assertFalse(fixed.isNotEmpty())
    }
}
