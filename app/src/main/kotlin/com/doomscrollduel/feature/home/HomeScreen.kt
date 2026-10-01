package com.doomscrollduel.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doomscrollduel.R
import com.doomscrollduel.core.designsystem.brain.BrainState
import com.doomscrollduel.core.designsystem.brain.BrainView
import com.doomscrollduel.core.designsystem.brain.ReelUsage
import com.doomscrollduel.core.designsystem.components.Avatar
import com.doomscrollduel.core.designsystem.components.ChunkyButton
import com.doomscrollduel.core.designsystem.components.ChunkyCard
import com.doomscrollduel.core.designsystem.components.CoinChip
import com.doomscrollduel.core.designsystem.components.DuelIcon
import com.doomscrollduel.core.designsystem.components.DuelIcons
import com.doomscrollduel.core.designsystem.components.DuelScreen
import com.doomscrollduel.core.designsystem.components.DuelTab
import com.doomscrollduel.core.designsystem.components.DuelText
import com.doomscrollduel.core.designsystem.components.HardShadowText
import com.doomscrollduel.core.designsystem.components.HpBar
import com.doomscrollduel.core.designsystem.components.StatusPill
import com.doomscrollduel.core.designsystem.components.StreakChip
import com.doomscrollduel.core.designsystem.components.TabbedScreenPreview
import com.doomscrollduel.core.designsystem.components.VsBadge
import com.doomscrollduel.core.designsystem.components.scaled
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import com.doomscrollduel.feature.FakeData

@Immutable
data class ActiveBattleUi(
    val opponentName: String,
    val myReels: Int,
    val opponentReels: Int,
    val timeLeft: String,
    val stakeCoins: Int,
)

@Immutable
data class HomeUiState(
    val userName: String,
    val streakDays: Int,
    val coins: Int,
    val reelsToday: Int,
    val reelLimit: Int,
    /** Null when no duel is running. */
    val battle: ActiveBattleUi?,
)

@Composable
fun HomeScreen(
    state: HomeUiState,
    onNewBattle: () -> Unit,
    onOpenBattle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val percent = ReelUsage.percentUsed(state.reelsToday, state.reelLimit)
    val brainState = BrainState.fromPercentUsed(percent)
    val hp = ReelUsage.hpForPercentUsed(percent)

    DuelScreen(
        modifier = modifier,
        bottom = {
            ChunkyButton(
                text = stringResource(R.string.home_new_battle),
                onClick = onNewBattle,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        HomeTopBar(state)
        Spacer(Modifier.height(4.dp.scaled()))
        BrainView(
            state = brainState,
            modifier = Modifier
                .width(190.dp.scaled())
                .align(Alignment.CenterHorizontally),
        )
        HardShadowText(
            text = state.reelsToday.toString(),
            fontSize = 96.sp.scaled(),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        DuelText(
            text = stringResource(R.string.home_reels_label),
            style = DuelTheme.typography.captionStrong,
            color = DuelTheme.colors.textMuted,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        DuelText(
            text = stringResource(R.string.home_limit_caption, state.reelLimit),
            style = DuelTheme.typography.caption,
            color = DuelTheme.colors.textMuted,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(12.dp.scaled()))
        DuelText(
            text = stringResource(R.string.home_brain_hp),
            style = DuelTheme.typography.captionStrong,
            color = DuelTheme.colors.textMuted,
        )
        Spacer(Modifier.height(4.dp))
        HpBar(
            hp = hp,
            modifier = Modifier.fillMaxWidth(),
            labelText = stringResource(R.string.home_hp_value, hp),
        )
        if (state.battle != null) {
            Spacer(Modifier.height(16.dp.scaled()))
            BattleCard(battle = state.battle, onClick = onOpenBattle)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HomeTopBar(state: HomeUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Avatar(name = state.userName, color = DuelTheme.colors.pink)
        DuelText(
            text = state.userName,
            style = DuelTheme.typography.bodyStrong,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        StreakChip(days = state.streakDays)
        CoinChip(coins = state.coins)
    }
}

@Composable
private fun BattleCard(battle: ActiveBattleUi, onClick: () -> Unit) {
    val colors = DuelTheme.colors
    ChunkyCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        onClickLabel = stringResource(R.string.home_open_battle),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DuelText(
                text = stringResource(R.string.home_battle_title),
                style = DuelTheme.typography.heading.copy(fontSize = 20.sp, lineHeight = 26.sp),
                modifier = Modifier.weight(1f),
            )
            StatusPill(
                text = stringResource(R.string.home_time_left, battle.timeLeft),
                fill = colors.yellow,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ScoreColumn(
                name = stringResource(R.string.label_you),
                reels = battle.myReels,
                nameColor = colors.pink,
            )
            VsBadge()
            ScoreColumn(name = battle.opponentName, reels = battle.opponentReels, nameColor = colors.cyan)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DuelIcon(DuelIcons.Coin, tint = colors.yellow, contentDescription = null, modifier = Modifier.size(18.dp))
            DuelText(
                text = stringResource(R.string.home_stake_line, battle.stakeCoins),
                style = DuelTheme.typography.caption,
                color = colors.textMuted,
            )
        }
    }
}

@Composable
private fun ScoreColumn(name: String, reels: Int, nameColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        DuelText(
            text = name,
            style = DuelTheme.typography.bodyStrong,
            color = nameColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        HardShadowText(text = reels.toString(), fontSize = 44.sp, shadowDepth = 4.dp)
    }
}

@Preview(name = "Home 390x844", widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenPreview() = TabbedScreenPreview(DuelTab.HOME) {
    HomeScreen(state = FakeData.home, onNewBattle = {}, onOpenBattle = {})
}

@Preview(name = "Home small 320x640", widthDp = 320, heightDp = 640)
@Composable
private fun HomeScreenSmallPreview() = TabbedScreenPreview(DuelTab.HOME) {
    HomeScreen(state = FakeData.home, onNewBattle = {}, onOpenBattle = {})
}

@Preview(name = "Home tablet 600x960", widthDp = 600, heightDp = 960)
@Composable
private fun HomeScreenTabletPreview() = TabbedScreenPreview(DuelTab.HOME) {
    HomeScreen(state = FakeData.home.copy(reelsToday = 104), onNewBattle = {}, onOpenBattle = {})
}
