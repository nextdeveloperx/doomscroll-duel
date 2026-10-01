package com.doomscrollduel.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.doomscrollduel.R
import com.doomscrollduel.feature.duel.result.ResultFighter
import com.doomscrollduel.feature.duel.result.ResultScreen
import com.doomscrollduel.feature.duel.result.ResultUiState
import com.doomscrollduel.feature.duel.result.rememberShareText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResultScreenTest {
    @get:Rule val rule = createComposeRule()

    private var shares = 0
    private var rematches = 0

    private fun state(me: Int, them: Int, stake: Int = 50) =
        ResultUiState(ResultFighter("Rohan", me), ResultFighter("Aman", them), reelLimit = 100, stakeCoins = stake)

    private fun show(s: ResultUiState) = rule.setDuelContent {
        ResultScreen(state = s, onShare = { shares++ }, onRematch = { rematches++ })
    }

    @Test fun lowerCountWinsAndTheWinnerHearsIt() {
        show(state(me = 31, them = 47))
        rule.onNodeWithText(str(R.string.result_winner_you)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.result_coins_won, 50)).assertIsDisplayed()
    }

    @Test fun higherCountLosesAndNamesTheWinner() {
        show(state(me = 104, them = 38))
        rule.onNodeWithText(str(R.string.result_winner_other, "AMAN")).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.result_coins_lost, 50)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.result_winner_you)).assertDoesNotExist()
    }

    @Test fun equalCountsAreADrawAndTheCoinsComeBack() {
        show(state(me = 60, them = 60))
        rule.onNodeWithText(str(R.string.result_draw)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.result_coins_draw)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.result_coins_won, 50)).assertDoesNotExist()
        rule.onNodeWithText(str(R.string.result_coins_lost, 50)).assertDoesNotExist()
    }

    @Test fun bothPlayersReelCountsAreShown() {
        show(state(me = 31, them = 47))
        rule.onNodeWithText(str(R.string.result_reels, 31)).performScrollTo().assertExists()
        rule.onNodeWithText(str(R.string.result_reels, 47)).performScrollTo().assertExists()
    }

    @Test fun theCoinAmountFollowsTheStake() {
        show(state(me = 1, them = 2, stake = 100))
        rule.onNodeWithText(str(R.string.result_coins_won, 100)).assertIsDisplayed()
    }

    @Test fun shareAndRematchButtonsCallBack() {
        show(state(me = 31, them = 47))
        rule.onNodeWithText(str(R.string.result_share)).performScrollTo().performClick()
        rule.onNodeWithText(str(R.string.result_rematch)).performScrollTo().performClick()
        assertEquals(1, shares)
        assertEquals(1, rematches)
    }

    @Test fun shareTextHasNamesAndCountsOnly() {
        lateinit var win: String
        lateinit var loss: String
        lateinit var draw: String
        rule.setDuelContent {
            win = rememberShareText(state(31, 47))
            loss = rememberShareText(state(104, 38))
            draw = rememberShareText(state(60, 60))
            Empty()
        }
        assertEquals(str(R.string.result_share_won, "Aman", 31, 47), win)
        assertEquals(str(R.string.result_share_lost, "Aman", 104, 38), loss)
        assertEquals(str(R.string.result_share_draw, "Aman", 60, 60), draw)
    }

    @Composable
    private fun Empty() = Unit
}
