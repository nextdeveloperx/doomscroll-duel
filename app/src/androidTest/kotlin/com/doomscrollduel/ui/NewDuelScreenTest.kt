package com.doomscrollduel.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.doomscrollduel.R
import com.doomscrollduel.feature.FakeData
import com.doomscrollduel.feature.duel.create.DurationOption
import com.doomscrollduel.feature.duel.create.MaxReelLimit
import com.doomscrollduel.feature.duel.create.MinReelLimit
import com.doomscrollduel.feature.duel.create.NewDuelScreen
import com.doomscrollduel.feature.duel.create.NewDuelUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * New Duel is driven like the real route: a small stateful wrapper keeps the form, so a tap changes what the screen shows.
 */
@RunWith(AndroidJUnit4::class)
class NewDuelScreenTest {
    @get:Rule val rule = createComposeRule()

    private var sent: NewDuelUiState? = null
    private var backs = 0
    private lateinit var current: () -> NewDuelUiState

    private fun show(initial: NewDuelUiState = FakeData.newDuel) {
        rule.setDuelContent {
            var state by mutableStateOf(initial)
            current = { state }
            NewDuelScreen(
                state = state,
                onFriendSelected = { state = state.copy(selectedFriendIndex = it) },
                onLimitChange = { state = state.copy(reelLimit = it.coerceIn(MinReelLimit, MaxReelLimit)) },
                onDurationSelected = { state = state.copy(duration = it) },
                onStakeSelected = { state = state.copy(stakeCoins = it) },
                onBack = { backs++ },
                onSend = { sent = state },
            )
        }
    }

    private fun explain(limit: Int, coins: Int, friend: String) = str(R.string.nd_explain, limit, coins, friend)

    @Test fun startsWithTheDefaultsExplainedInPlainWords() {
        show()
        rule.onNodeWithText(str(R.string.nd_title)).assertIsDisplayed()
        rule.onNodeWithText(explain(100, 50, "Aman")).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(str(R.string.nd_virtual_note)).performScrollTo().assertIsDisplayed()
    }

    @Test fun choosingAStakeUpdatesTheExplanation() {
        show()
        rule.onNodeWithText(str(R.string.nd_stake_option, 100)).performScrollTo().performClick()
        rule.onNodeWithText(explain(100, 100, "Aman")).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(str(R.string.nd_stake_option, 25)).performScrollTo().performClick()
        rule.onNodeWithText(explain(100, 25, "Aman")).performScrollTo().assertIsDisplayed()
    }

    @Test fun choosingAFriendChangesWhoTheCoinsGoTo() {
        show()
        rule.onNodeWithText("Riya").performScrollTo().performClick()
        rule.onNodeWithText(explain(100, 50, "Riya")).performScrollTo().assertIsDisplayed()
    }

    @Test fun limitStepsByTenAndStopsAtTheEnds() {
        show(FakeData.newDuel.copy(reelLimit = 100))
        rule.onNodeWithContentDescription(str(R.string.nd_limit_increase)).performScrollTo().performClick()
        assertEquals(110, current().reelLimit)
        rule.onNodeWithContentDescription(str(R.string.nd_limit_decrease)).performClick()
        rule.onNodeWithContentDescription(str(R.string.nd_limit_decrease)).performClick()
        assertEquals(90, current().reelLimit)
    }

    @Test fun limitCannotGoBelowTheMinimumOrAboveTheMaximum() {
        show(FakeData.newDuel.copy(reelLimit = MinReelLimit))
        rule.onNodeWithContentDescription(str(R.string.nd_limit_decrease)).performScrollTo().assertIsNotEnabled()
        rule.onNodeWithContentDescription(str(R.string.nd_limit_increase)).assertIsEnabled()
    }

    @Test fun limitAtTheMaximumDisablesIncrease() {
        show(FakeData.newDuel.copy(reelLimit = MaxReelLimit))
        rule.onNodeWithContentDescription(str(R.string.nd_limit_increase)).performScrollTo().assertIsNotEnabled()
    }

    @Test fun durationChipsSelectOneAtATime() {
        show()
        rule.onNodeWithText(str(R.string.nd_duration_7d)).performScrollTo().performClick()
        assertEquals(DurationOption.SEVEN_DAYS, current().duration)
        rule.onNodeWithText(str(R.string.nd_duration_6h)).performClick()
        assertEquals(DurationOption.SIX_HOURS, current().duration)
    }

    @Test fun sendHandsOverExactlyWhatWasChosen() {
        show()
        rule.onNodeWithText(str(R.string.nd_stake_option, 100)).performScrollTo().performClick()
        rule.onNodeWithText(str(R.string.nd_duration_7d)).performScrollTo().performClick()
        rule.onNodeWithText(str(R.string.nd_send)).performClick()
        val form = requireNotNull(sent) { "send did not call back" }
        assertEquals(100, form.stakeCoins)
        assertEquals(DurationOption.SEVEN_DAYS, form.duration)
        assertEquals(100, form.reelLimit)
        assertEquals("Aman", form.friendName)
    }

    @Test fun backButtonLeaves() {
        show()
        rule.onNodeWithContentDescription(str(R.string.ds_back)).performClick()
        assertEquals(1, backs)
    }
}
