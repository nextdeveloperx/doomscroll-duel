package com.doomscrollduel.feature.duel.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doomscrollduel.R
import com.doomscrollduel.core.designsystem.brain.BrainOwner
import com.doomscrollduel.core.designsystem.components.ChunkyButton
import com.doomscrollduel.core.designsystem.components.ChunkyButtonStyle
import com.doomscrollduel.core.designsystem.components.ChunkyCard
import com.doomscrollduel.core.designsystem.components.DuelScreen
import com.doomscrollduel.core.designsystem.components.DuelText
import com.doomscrollduel.core.designsystem.components.FighterPanel
import com.doomscrollduel.core.designsystem.components.ScreenPreview
import com.doomscrollduel.core.designsystem.components.StatusPill
import com.doomscrollduel.core.designsystem.components.VsBadge
import com.doomscrollduel.core.designsystem.components.scaled
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import com.doomscrollduel.feature.FakeData

@Composable
fun LiveDuelScreen(
    state: LiveDuelUiState,
    onRoast: () -> Unit,
    onSeeResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DuelScreen(
        modifier = modifier,
        bottom = {
            ChunkyButton(
                text = stringResource(R.string.live_roast),
                onClick = onRoast,
                style = ChunkyButtonStyle.Opponent,
                modifier = Modifier.fillMaxWidth(),
            )
            ChunkyButton(
                text = stringResource(R.string.live_result),
                onClick = onSeeResult,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        LiveHeader(timeLeft = state.timeLeft)
        Arena(state)
        Spacer(Modifier.height(16.dp))
        WarningCard(state)
        LeadLine(state)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LiveHeader(timeLeft: String) {
    val spoken = stringResource(R.string.live_timer_cd, timeLeft)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StatusPill(
            text = stringResource(R.string.live_pill),
            fill = DuelTheme.colors.red,
            pulsingDot = true,
        )
        DuelText(
            text = timeLeft,
            style = DuelTheme.typography.title,
            maxLines = 1,
            modifier = Modifier.semantics { contentDescription = spoken },
        )
    }
}

@Composable
private fun Arena(state: LiveDuelUiState) {
    ChunkyCard(modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                FighterPanel(
                    name = stringResource(R.string.label_you),
                    reels = state.me.reels,
                    limit = state.reelLimit,
                    owner = BrainOwner.YOU,
                    brainWidth = 104.dp.scaled(),
                    countSize = 52.sp.scaled(),
                    modifier = Modifier.weight(1f),
                )
                FighterPanel(
                    name = state.opponent.name,
                    reels = state.opponent.reels,
                    limit = state.reelLimit,
                    owner = BrainOwner.OPPONENT,
                    brainWidth = 104.dp.scaled(),
                    countSize = 52.sp.scaled(),
                    modifier = Modifier.weight(1f),
                )
            }
            VsBadge(Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun WarningCard(state: LiveDuelUiState) {
    val colors = DuelTheme.colors
    val message = when (val warning = state.warning) {
        LiveWarning.None -> return
        is LiveWarning.Close -> stringResource(
            R.string.live_warning_close, warning.reelsLeft, state.stakeCoins, state.opponent.name,
        )
        LiveWarning.LimitReached -> stringResource(
            R.string.live_warning_over, state.stakeCoins, state.opponent.name,
        )
    }
    ChunkyCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Assertive },
        fill = colors.red,
    ) {
        DuelText(
            text = message,
            style = DuelTheme.typography.bodyStrong,
            color = colors.onBright,
        )
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun LeadLine(state: LiveDuelUiState) {
    val colors = DuelTheme.colors
    val (text, color) = when (val lead = state.lead) {
        is Lead.You -> stringResource(R.string.live_lead_you, lead.by) to colors.pink
        is Lead.Opponent -> stringResource(R.string.live_lead_opponent, state.opponent.name, lead.by) to colors.cyan
        Lead.Tied -> stringResource(R.string.live_lead_tied) to colors.yellow
    }
    DuelText(
        text = text,
        style = DuelTheme.typography.button,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Preview(name = "Live duel 390x844 (near limit)", widthDp = 390, heightDp = 844)
@Composable
private fun LiveDuelPreview() = ScreenPreview {
    LiveDuelScreen(state = FakeData.live, onRoast = {}, onSeeResult = {})
}

@Preview(name = "Live duel safe 390x844", widthDp = 390, heightDp = 844)
@Composable
private fun LiveDuelSafePreview() = ScreenPreview {
    LiveDuelScreen(
        state = FakeData.live.copy(me = FakeData.live.me.copy(reels = 41)),
        onRoast = {}, onSeeResult = {},
    )
}

@Preview(name = "Live duel small 320x640", widthDp = 320, heightDp = 640)
@Composable
private fun LiveDuelSmallPreview() = ScreenPreview {
    LiveDuelScreen(state = FakeData.live, onRoast = {}, onSeeResult = {})
}
