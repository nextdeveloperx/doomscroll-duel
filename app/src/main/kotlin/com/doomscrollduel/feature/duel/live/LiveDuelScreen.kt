package com.doomscrollduel.feature.duel.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.doomscrollduel.feature.FakeData
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import com.doomscrollduel.core.designsystem.components.ScreenPreview
import com.doomscrollduel.feature.common.Kit
import com.doomscrollduel.feature.common.KitButton
import com.doomscrollduel.feature.common.KitButtonKind
import com.doomscrollduel.feature.common.KitBottomButton
import com.doomscrollduel.feature.common.KitCard
import com.doomscrollduel.feature.common.KitFighter
import com.doomscrollduel.feature.common.KitPage
import com.doomscrollduel.feature.common.KitVs
import com.doomscrollduel.feature.settings.neon.NText
import com.doomscrollduel.feature.settings.neon.NeonIcons

@Composable
fun LiveDuelScreen(
    state: LiveDuelUiState,
    onRoast: () -> Unit,
    onSeeResult: () -> Unit,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit = {},
    onClose: () -> Unit = {},
    onLeave: () -> Unit = {},
) {
    var confirmLeave by remember { mutableStateOf(false) }
    KitPage(
        modifier = modifier,
        bottomSpace = 92.dp,
        bottom = {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (state.phase) {
                    LivePhase.ACTIVE -> {
                        KitButton(
                            text = stringResource(if (confirmLeave) R.string.live_leave_confirm else R.string.live_leave),
                            onClick = { if (confirmLeave) onLeave() else confirmLeave = true },
                            kind = if (confirmLeave) KitButtonKind.Danger else KitButtonKind.Secondary,
                        )
                    }
                    LivePhase.ENDED -> KitButton(stringResource(R.string.live_result), onSeeResult)
                    LivePhase.WAITING ->
                        if (state.canCancel) KitButton(stringResource(R.string.live_cancel), onCancel, kind = KitButtonKind.Secondary)
                        else KitButton(stringResource(R.string.live_back), onClose, kind = KitButtonKind.Secondary)
                    LivePhase.CLOSED -> KitButton(stringResource(R.string.live_back), onClose)
                }
            }
        },
    ) {
        LiveHeader(timeLeft = state.timeLeft, phase = state.phase)
        Arena(state)
        Spacer(Modifier.height(16.dp))
        when (state.phase) {
            LivePhase.WAITING -> NoteCard(stringResource(R.string.live_waiting, state.opponent.name), Kit.Gold)
            LivePhase.CLOSED -> NoteCard(stringResource(R.string.live_closed), Kit.Red)
            LivePhase.ENDED -> NoteCard(stringResource(R.string.live_ended), Kit.Violet)
            LivePhase.ACTIVE -> Unit
        }
        if (state.phase == LivePhase.ACTIVE || state.phase == LivePhase.ENDED) {
            if (state.phase == LivePhase.ACTIVE) WarningCard(state)
            LeadLine(state)
        }
    }
}

/** Shown instead of the battle while it loads, or when it does not exist (or is not mine). */
@Composable
fun LiveDuelMissing(onClose: () -> Unit, modifier: Modifier = Modifier) {
    KitPage(
        modifier = modifier,
        bottom = { KitBottomButton(stringResource(R.string.live_back), onClose, kind = KitButtonKind.Secondary) },
    ) {
        Spacer(Modifier.height(24.dp))
        NoteCard(stringResource(R.string.live_loading), Kit.Violet)
    }
}

@Composable
private fun NoteCard(text: String, tone: androidx.compose.ui.graphics.Color) {
    KitCard(
        fill = Brush.horizontalGradient(listOf(tone.copy(alpha = 0.2f), tone.copy(alpha = 0.2f))),
        edge = tone.copy(alpha = 0.5f),
    ) {
        NText(text, 15.sp, weight = FontWeight.Bold, lineHeight = 21.sp)
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun LiveHeader(timeLeft: String, phase: LivePhase) {
    val spoken = stringResource(R.string.live_timer_cd, timeLeft)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Kit.Red.copy(alpha = 0.18f))
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(Kit.Red))
            NText(stringResource(if (phase == LivePhase.ACTIVE) R.string.live_pill else R.string.live_pill_idle), 13.sp, color = Kit.Red, weight = FontWeight.ExtraBold, maxLines = 1)
        }
        NText(
            text = timeLeft,
            size = 28.sp,
            weight = FontWeight.Black,
            maxLines = 1,
            modifier = Modifier.semantics { contentDescription = spoken },
        )
    }
}

@Composable
private fun Arena(state: LiveDuelUiState) {
    KitCard(radius = 26.dp, padding = PaddingValues(horizontal = 12.dp, vertical = 18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            KitFighter(stringResource(R.string.label_you), state.me.reels, state.reelLimit, BrainOwner.YOU, Modifier.weight(1f), brainWidth = 104.dp)
            KitVs()
            KitFighter(state.opponent.name, state.opponent.reels, state.reelLimit, BrainOwner.OPPONENT, Modifier.weight(1f), brainWidth = 104.dp)
        }
    }
}

@Composable
private fun WarningCard(state: LiveDuelUiState) {
    val message = when (val warning = state.warning) {
        LiveWarning.None -> return
        is LiveWarning.Close -> stringResource(
            R.string.live_warning_close, warning.reelsLeft, state.stakeCoins, state.opponent.name,
        )
        LiveWarning.LimitReached -> stringResource(
            R.string.live_warning_over, state.stakeCoins, state.opponent.name,
        )
    }
    KitCard(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        fill = Brush.horizontalGradient(listOf(Kit.Red.copy(alpha = 0.22f), Kit.Red.copy(alpha = 0.22f))),
        edge = Kit.Red.copy(alpha = 0.5f),
    ) {
        NText(message, 15.sp, weight = FontWeight.Bold, lineHeight = 21.sp)
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun LeadLine(state: LiveDuelUiState) {
    val (text, color) = when (val lead = state.lead) {
        is Lead.You -> stringResource(R.string.live_lead_you, lead.by) to Kit.Pink
        is Lead.Opponent -> stringResource(R.string.live_lead_opponent, state.opponent.name, lead.by) to Kit.Blue
        Lead.Tied -> stringResource(R.string.live_lead_tied) to Kit.Gold
    }
    NText(
        text = text,
        size = 17.sp,
        weight = FontWeight.ExtraBold,
        color = color,
        align = TextAlign.Center,
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
