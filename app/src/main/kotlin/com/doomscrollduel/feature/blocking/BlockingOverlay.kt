package com.doomscrollduel.feature.blocking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
import com.doomscrollduel.core.designsystem.brain.BrainState
import com.doomscrollduel.core.designsystem.brain.BrainView
import com.doomscrollduel.core.designsystem.components.ChunkyButton
import com.doomscrollduel.core.designsystem.components.ChunkyButtonStyle
import com.doomscrollduel.core.designsystem.components.DuelText
import com.doomscrollduel.core.designsystem.components.HardShadowText
import com.doomscrollduel.core.designsystem.components.StatusPill
import com.doomscrollduel.core.designsystem.components.rememberLoopPhase
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import com.doomscrollduel.domain.blocking.BlockReason
import com.doomscrollduel.domain.blocking.GateState
import com.doomscrollduel.domain.blocking.TimeFormat
import kotlin.math.PI
import kotlin.math.cos

/** What the full-screen overlay shows right now. */
sealed interface OverlayUi {
    data object Hidden : OverlayUi

    /** "Brain bachao": the reel screen is blocked. */
    data class Block(
        val reason: BlockReason,
        val remainingMs: Long,
        val canAskFriend: Boolean,
        val askQuotaLeft: Int,
        val ask: AskUi,
    ) : OverlayUi

    /** "Wait-10": a countdown card before the reels. */
    data class Gate(val state: GateState) : OverlayUi
}

/** Where the "ask a friend" request is. */
enum class AskUi {
    Idle,
    Sending,
    Waiting,

    /** Could not reach the server. The request was not sent and did not use up one of the 3. */
    NoNetwork,
    QuotaUsed,
    NotSignedIn,
    Failed,

    /** The friend said no, or did not answer in 30 minutes. */
    Ended,
}

class OverlayActions(
    /** Gate: "Rehne do". Leaves the reel screen. */
    val onRehneDo: () -> Unit,
    /** Gate: "Dekhna hai", enabled after 10 seconds. */
    val onContinue: () -> Unit,
    /** Block: "Theek hai". Closes the overlay and leaves the app's reels. */
    val onDismissBlock: () -> Unit,
    val onAskFriend: () -> Unit,
)

@Composable
fun BlockingOverlay(ui: OverlayUi, actions: OverlayActions, modifier: Modifier = Modifier) {
    when (ui) {
        OverlayUi.Hidden -> Unit
        is OverlayUi.Block -> BrainBachaoScreen(ui, actions, modifier)
        is OverlayUi.Gate -> WaitGateScreen(ui.state, actions, modifier)
    }
}

/** Dark, opaque ground so the reel underneath cannot be seen or heard-about. */
@Composable
private fun OverlayGround(modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DuelTheme.colors.background)
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.navigationBars),
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ----- Brain bachao ---------------------------------------------------------------------------------

@Composable
private fun BrainBachaoScreen(ui: OverlayUi.Block, actions: OverlayActions, modifier: Modifier) {
    val colors = DuelTheme.colors
    val reasonText = stringResource(
        when (ui.reason) {
            BlockReason.STRICT_LOCK -> R.string.overlay_reason_lock
            BlockReason.BEDTIME -> R.string.overlay_reason_bedtime
            BlockReason.FOCUS -> R.string.overlay_reason_focus
        },
    )
    val clock = TimeFormat.clock(ui.remainingMs)
    val spoken = stringResource(R.string.overlay_time_left_cd, TimeFormat.words(ui.remainingMs))

    OverlayGround(modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HardShadowText(
                text = stringResource(R.string.overlay_title),
                fontSize = 40.sp,
                color = colors.yellow,
                maxLines = 2,
            )
            BrainView(BrainState.FRIED, Modifier.width(170.dp))
            DuelText(
                text = reasonText,
                style = DuelTheme.typography.bodyStrong,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
            // The countdown is read once as words, not every second.
            HardShadowText(
                text = clock,
                fontSize = 64.sp,
                modifier = Modifier.semantics { contentDescription = spoken },
            )
            Spacer(Modifier.height(4.dp))
            if (ui.canAskFriend) {
                ChunkyButton(
                    text = stringResource(R.string.overlay_ask_friend),
                    onClick = actions.onAskFriend,
                    style = ChunkyButtonStyle.Opponent,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AskStatusLine(ui)
            ChunkyButton(
                text = stringResource(R.string.overlay_ok),
                onClick = actions.onDismissBlock,
                style = ChunkyButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AskStatusLine(ui: OverlayUi.Block) {
    val text = when (ui.ask) {
        AskUi.Idle -> if (ui.canAskFriend) stringResource(R.string.overlay_ask_left, ui.askQuotaLeft) else null
        AskUi.Sending -> stringResource(R.string.overlay_ask_sending)
        AskUi.Waiting -> stringResource(R.string.overlay_ask_waiting)
        AskUi.NoNetwork -> stringResource(R.string.overlay_ask_no_network)
        AskUi.QuotaUsed -> stringResource(R.string.overlay_ask_quota_used)
        AskUi.NotSignedIn -> stringResource(R.string.overlay_ask_not_signed_in)
        AskUi.Failed -> stringResource(R.string.overlay_ask_failed)
        AskUi.Ended -> stringResource(R.string.overlay_ask_ended)
    } ?: return
    DuelText(
        text = text,
        style = DuelTheme.typography.caption,
        color = DuelTheme.colors.textMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

// ----- Wait-10 gate -----------------------------------------------------------------------------------

private const val BREATH_MILLIS = 8_000

@Composable
private fun WaitGateScreen(state: GateState, actions: OverlayActions, modifier: Modifier) {
    val colors = DuelTheme.colors
    val ready = state !is GateState.Counting
    val secondsLeft = (state as? GateState.Counting)?.let { (it.remainingMs + 999) / 1000 } ?: 0L
    val phase = rememberLoopPhase(BREATH_MILLIS, label = "breath")
    // True while breathing in. Only flips twice per cycle, so the text does not recompose every frame.
    val breathingIn by remember(phase) { derivedStateOf { phase.value < 0.5f } }

    OverlayGround(modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DuelText(
                text = stringResource(R.string.gate_title),
                style = DuelTheme.typography.title,
                textAlign = TextAlign.Center,
            )
            // A slow breathing brain: grows while breathing in, shrinks while breathing out. Still when the
            // user turned animations off.
            BrainView(
                state = BrainState.HAPPY,
                modifier = Modifier
                    .width(190.dp)
                    .graphicsLayer {
                        val wave = 0.5f - 0.5f * cos(phase.value * 2f * PI.toFloat())
                        val scale = 0.88f + 0.22f * wave
                        scaleX = scale
                        scaleY = scale
                    },
            )
            DuelText(
                text = stringResource(if (breathingIn) R.string.gate_breathe_in else R.string.gate_breathe_out),
                style = DuelTheme.typography.heading,
                color = colors.cyan,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (!ready) {
                HardShadowText(
                    text = secondsLeft.toString(),
                    fontSize = 72.sp,
                    modifier = Modifier.semantics { contentDescription = "" },
                )
            }
            Spacer(Modifier.height(4.dp))
            ChunkyButton(
                text = stringResource(R.string.gate_leave),
                onClick = actions.onRehneDo,
                modifier = Modifier.fillMaxWidth(),
            )
            ChunkyButton(
                text = if (ready) stringResource(R.string.gate_continue) else stringResource(R.string.gate_continue_wait, secondsLeft.toInt()),
                onClick = actions.onContinue,
                enabled = ready,
                style = ChunkyButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val NoActions = OverlayActions({}, {}, {}, {})

@Preview(name = "Brain bachao: lock", widthDp = 390, heightDp = 844)
@Composable
private fun BrainBachaoLockPreview() = DuelTheme {
    BlockingOverlay(OverlayUi.Block(BlockReason.STRICT_LOCK, 4_980_000L, canAskFriend = true, askQuotaLeft = 2, ask = AskUi.Idle), NoActions)
}

@Preview(name = "Brain bachao: bedtime, asked", widthDp = 390, heightDp = 844)
@Composable
private fun BrainBachaoBedtimePreview() = DuelTheme {
    BlockingOverlay(OverlayUi.Block(BlockReason.BEDTIME, 21_000_000L, canAskFriend = false, askQuotaLeft = 2, ask = AskUi.Waiting), NoActions)
}

@Preview(name = "Brain bachao: no network small", widthDp = 320, heightDp = 640)
@Composable
private fun BrainBachaoNoNetworkPreview() = DuelTheme {
    BlockingOverlay(OverlayUi.Block(BlockReason.FOCUS, 1_800_000L, canAskFriend = true, askQuotaLeft = 3, ask = AskUi.NoNetwork), NoActions)
}

@Preview(name = "Wait 10: counting", widthDp = 390, heightDp = 844)
@Composable
private fun WaitGateCountingPreview() = DuelTheme {
    BlockingOverlay(OverlayUi.Gate(GateState.Counting(7_000L)), NoActions)
}

@Preview(name = "Wait 10: ready", widthDp = 390, heightDp = 844)
@Composable
private fun WaitGateReadyPreview() = DuelTheme {
    BlockingOverlay(OverlayUi.Gate(GateState.Ready), NoActions)
}
