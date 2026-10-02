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
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import com.doomscrollduel.core.designsystem.brain.BrainState
import com.doomscrollduel.core.designsystem.brain.BrainView
import com.doomscrollduel.domain.blocking.BlockReason
import com.doomscrollduel.domain.blocking.GateState
import com.doomscrollduel.domain.blocking.TimeFormat
import kotlin.math.PI
import kotlin.math.cos
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.doomscrollduel.core.designsystem.components.rememberLoopPhase
import com.doomscrollduel.feature.common.Kit
import com.doomscrollduel.feature.common.KitButton
import com.doomscrollduel.feature.common.KitButtonKind
import com.doomscrollduel.feature.common.KitPill
import com.doomscrollduel.feature.modes.navyBackground
import com.doomscrollduel.feature.settings.neon.NText
import com.doomscrollduel.feature.settings.neon.Neon

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

/** Opaque ground so the reel underneath cannot be seen or heard-about. */
@Composable
private fun OverlayGround(modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF050818))
            .navyBackground()
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.navigationBars),
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ----- Brain bachao ---------------------------------------------------------------------------------

@Composable
private fun BrainBachaoScreen(ui: OverlayUi.Block, actions: OverlayActions, modifier: Modifier) {
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            NText(stringResource(R.string.overlay_title), 36.sp, weight = FontWeight.Black, color = Kit.Gold, maxLines = 2, align = TextAlign.Center, lineHeight = 42.sp)
            BrainView(BrainState.FRIED, Modifier.width(170.dp))
            KitPill(reasonText, tone = Kit.Violet)
            // The countdown is read once as words, not every second.
            NText(
                text = clock,
                size = 60.sp,
                weight = FontWeight.Black,
                modifier = Modifier.semantics { contentDescription = spoken },
            )
            Spacer(Modifier.height(4.dp))
            if (ui.canAskFriend) {
                KitButton(stringResource(R.string.overlay_ask_friend), actions.onAskFriend)
            }
            AskStatusLine(ui)
            KitButton(stringResource(R.string.overlay_ok), actions.onDismissBlock, kind = KitButtonKind.Secondary)
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
    NText(
        text = text,
        size = 13.sp,
        color = Neon.Muted,
        align = TextAlign.Center,
        lineHeight = 18.sp,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

// ----- Wait-10 gate -----------------------------------------------------------------------------------

private const val BREATH_MILLIS = 8_000

@Composable
private fun WaitGateScreen(state: GateState, actions: OverlayActions, modifier: Modifier) {
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            NText(stringResource(R.string.gate_title), 30.sp, weight = FontWeight.Black, align = TextAlign.Center, lineHeight = 36.sp)
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
            NText(
                text = stringResource(if (breathingIn) R.string.gate_breathe_in else R.string.gate_breathe_out),
                size = 20.sp,
                weight = FontWeight.ExtraBold,
                color = Kit.Blue,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (!ready) {
                NText(
                    text = secondsLeft.toString(),
                    size = 64.sp,
                    weight = FontWeight.Black,
                    modifier = Modifier.semantics { contentDescription = "" },
                )
            }
            Spacer(Modifier.height(4.dp))
            KitButton(stringResource(R.string.gate_leave), actions.onRehneDo)
            KitButton(
                text = if (ready) stringResource(R.string.gate_continue) else stringResource(R.string.gate_continue_wait, secondsLeft.toInt()),
                onClick = actions.onContinue,
                enabled = ready,
                kind = KitButtonKind.Secondary,
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

@Preview(name = "Wait 10: counting", widthDp = 390, heightDp = 844)
@Composable
private fun WaitGateCountingPreview() = DuelTheme {
    BlockingOverlay(OverlayUi.Gate(GateState.Counting(7_000L)), NoActions)
}
