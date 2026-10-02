package com.doomscrollduel.feature.duel.result

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doomscrollduel.R
import com.doomscrollduel.core.designsystem.brain.BrainOwner
import com.doomscrollduel.core.designsystem.brain.BrainState
import com.doomscrollduel.core.designsystem.brain.BrainView
import com.doomscrollduel.core.designsystem.brain.ReelUsage
import com.doomscrollduel.domain.usecase.DuelOutcome
import com.doomscrollduel.domain.usecase.ResolveDuel
import com.doomscrollduel.feature.FakeData
import kotlin.math.PI
import kotlin.random.Random
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import com.doomscrollduel.core.designsystem.components.DuelIcon
import com.doomscrollduel.core.designsystem.components.ScreenPreview
import com.doomscrollduel.core.designsystem.components.rememberLoopPhase
import com.doomscrollduel.feature.common.Kit
import com.doomscrollduel.feature.common.KitButton
import com.doomscrollduel.feature.common.KitButtonKind
import com.doomscrollduel.feature.common.KitCard
import com.doomscrollduel.feature.common.KitPage
import com.doomscrollduel.feature.common.KitPill
import com.doomscrollduel.feature.modes.BannerWord
import com.doomscrollduel.feature.settings.neon.NText
import com.doomscrollduel.feature.settings.neon.Neon
import com.doomscrollduel.feature.settings.neon.NeonIcons
import com.doomscrollduel.core.designsystem.theme.DuelTheme

@Immutable
data class ResultFighter(val name: String, val reels: Int)

@Immutable
data class ResultUiState(
    val me: ResultFighter,
    val opponent: ResultFighter,
    val reelLimit: Int,
    val stakeCoins: Int,
    /** False while coins cannot be moved (no server yet): the screen then says so instead of "tum 50 coins jeete". */
    val coinsSettled: Boolean = true,
    /** The battle is still running, so these are the scores so far. */
    val inProgress: Boolean = false,
) {
    val outcome: DuelOutcome get() = ResolveDuel.forMe(me.reels, opponent.reels)
}

/** Text for the system share sheet. Only names and counts, nothing about what was watched. */
@Composable
fun rememberShareText(state: ResultUiState): String {
    val resId = when (state.outcome) {
        DuelOutcome.WIN -> R.string.result_share_won
        DuelOutcome.LOSS -> R.string.result_share_lost
        DuelOutcome.DRAW -> R.string.result_share_draw
    }
    return stringResource(resId, state.opponent.name, state.me.reels, state.opponent.reels)
}

@Composable
fun ResultScreen(
    state: ResultUiState,
    onShare: () -> Unit,
    onRematch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outcome = state.outcome
    val headline = when (outcome) {
        DuelOutcome.WIN -> stringResource(R.string.result_winner_you)
        DuelOutcome.LOSS -> stringResource(R.string.result_winner_other, state.opponent.name.uppercase())
        DuelOutcome.DRAW -> stringResource(R.string.result_draw)
    }
    val headlineTone = when (outcome) {
        DuelOutcome.WIN -> Kit.Gold
        DuelOutcome.LOSS -> Kit.Blue
        DuelOutcome.DRAW -> Kit.Violet
    }
    val myState = brainStateOf(state.me.reels, state.reelLimit)
    val opponentState = brainStateOf(state.opponent.reels, state.reelLimit)

    Box(modifier.fillMaxSize()) {
        KitPage(
            bottomSpace = 150.dp,
            bottom = {
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    KitButton(stringResource(R.string.result_share), onShare)
                    KitButton(stringResource(R.string.result_rematch), onRematch, kind = KitButtonKind.Secondary)
                }
            },
        ) {
            Spacer(Modifier.height(12.dp))
            if (state.inProgress) {
                KitPill(stringResource(R.string.result_in_progress), tone = Kit.Gold, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(8.dp))
            }
            NText(
                text = headline,
                size = 36.sp,
                weight = FontWeight.Black,
                color = headlineTone,
                maxLines = 2,
                align = TextAlign.Center,
                lineHeight = 40.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            // The winner wears the crown. On a draw nobody does and we show your own brain.
            val (heroState, heroOwner) = when (outcome) {
                DuelOutcome.LOSS -> opponentState to BrainOwner.OPPONENT
                else -> myState to BrainOwner.YOU
            }
            BrainView(
                state = heroState,
                owner = heroOwner,
                crowned = outcome != DuelOutcome.DRAW,
                modifier = Modifier
                    .width(190.dp)
                    .align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ScoreCard(
                    name = stringResource(R.string.label_you),
                    reels = state.me.reels,
                    brain = myState,
                    owner = BrainOwner.YOU,
                    modifier = Modifier.weight(1f),
                )
                ScoreCard(
                    name = state.opponent.name,
                    reels = state.opponent.reels,
                    brain = opponentState,
                    owner = BrainOwner.OPPONENT,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(14.dp))
            CoinsCard(outcome = outcome, coins = state.stakeCoins, settled = state.coinsSettled)
        }
        if (outcome == DuelOutcome.WIN && !state.inProgress) Confetti(Modifier.fillMaxWidth().height(360.dp))
    }
}

private fun brainStateOf(reels: Int, limit: Int): BrainState = ReelUsage.brainState(reels, limit)

@Composable
private fun ScoreCard(
    name: String,
    reels: Int,
    brain: BrainState,
    owner: BrainOwner,
    modifier: Modifier = Modifier,
) {
    val nameColor = if (owner == BrainOwner.YOU) Kit.Pink else Kit.Blue
    val (stateLabel, stateTone) = when (brain) {
        BrainState.HAPPY -> stringResource(R.string.result_state_happy) to Kit.Green
        BrainState.FRIED -> stringResource(R.string.result_state_fried) to Kit.Orange
        BrainState.ZOMBIE -> stringResource(R.string.result_state_zombie) to Kit.Red
    }
    KitCard(modifier = modifier, radius = 24.dp, padding = PaddingValues(12.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            NText(name, 15.sp, color = nameColor, weight = FontWeight.ExtraBold, maxLines = 1)
            BrainView(state = brain, owner = owner, modifier = Modifier.width(84.dp))
            BannerWord(reels.toString(), listOf(Color.White, Color(0xFFE3EAFF), Color(0xFF9DB4FF)), Color(0xFF2B1A8C), nameColor, 40.sp)
            NText(stringResource(R.string.result_reels, reels), 12.sp, color = Neon.Muted, maxLines = 1)
            KitPill(stateLabel, tone = stateTone)
        }
    }
}

@Composable
private fun CoinsCard(outcome: DuelOutcome, coins: Int, settled: Boolean) {
    if (!settled) {
        KitCard(radius = 22.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DuelIcon(NeonIcons.Coin, tint = Kit.Gold, contentDescription = null, modifier = Modifier.size(26.dp))
                NText(stringResource(R.string.result_coins_pending, coins), 14.sp, weight = FontWeight.Bold, lineHeight = 20.sp, modifier = Modifier.weight(1f))
            }
        }
        return
    }
    val (text, tone) = when (outcome) {
        DuelOutcome.WIN -> stringResource(R.string.result_coins_won, coins) to Kit.Green
        DuelOutcome.LOSS -> stringResource(R.string.result_coins_lost, coins) to Kit.Red
        DuelOutcome.DRAW -> stringResource(R.string.result_coins_draw) to Kit.Violet
    }
    KitCard(
        fill = Brush.horizontalGradient(listOf(tone.copy(alpha = 0.20f), tone.copy(alpha = 0.20f))),
        edge = tone.copy(alpha = 0.45f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            DuelIcon(NeonIcons.Coin, tint = tone, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            NText(text, 18.sp, weight = FontWeight.ExtraBold, align = TextAlign.Center)
        }
    }
}

private class ConfettiPiece(
    val x: Float,
    val y: Float,
    val size: Float,
    val angle: Float,
    val spin: Int,
    val fall: Int,
    val shape: Int,
    val colorIndex: Int,
)

/**
 * Decorative falling shapes behind the headline of a win. Whole-number fall and spin cycles make the loop seamless;
 * with animations turned off the pieces simply stay where they are.
 */
@Composable
private fun Confetti(modifier: Modifier = Modifier) {
    val colors = DuelTheme.colors
    val palette = remember(colors) {
        listOf(colors.yellow, colors.pink, colors.cyan, colors.green, colors.orange, colors.lavender)
    }
    val pieces = remember {
        val random = Random(7)
        List(28) {
            ConfettiPiece(
                x = random.nextFloat(),
                y = random.nextFloat(),
                size = 8f + random.nextFloat() * 10f,
                angle = random.nextFloat() * 360f,
                spin = random.nextInt(1, 3),
                fall = random.nextInt(1, 3),
                shape = random.nextInt(3),
                colorIndex = random.nextInt(palette.size),
            )
        }
    }
    val phase = rememberLoopPhase(durationMillis = 9000, label = "confetti")

    Canvas(modifier) {
        val t = phase.value
        pieces.forEach { piece ->
            val cx = piece.x * size.width
            val cy = ((piece.y + t * piece.fall) % 1f) * size.height
            val s = piece.size.dp.toPx()
            val color: Color = palette[piece.colorIndex].copy(alpha = 0.85f)
            translate(cx, cy) {
                rotate(piece.angle + t * 360f * piece.spin, pivot = Offset.Zero) {
                    when (piece.shape) {
                        0 -> drawCircle(color, radius = s / 2f)
                        1 -> drawRect(color, topLeft = Offset(-s / 2f, -s / 3f), size = Size(s, s * 0.66f))
                        else -> drawPath(
                            Path().apply {
                                moveTo(0f, -s / 2f)
                                lineTo(s / 2f, s / 2f)
                                lineTo(-s / 2f, s / 2f)
                                close()
                            },
                            color,
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Result - lost 390x844", widthDp = 390, heightDp = 844)
@Composable
private fun ResultLostPreview() = ScreenPreview {
    ResultScreen(state = FakeData.result, onShare = {}, onRematch = {})
}

@Preview(name = "Result - won 390x844", widthDp = 390, heightDp = 844)
@Composable
private fun ResultWonPreview() = ScreenPreview {
    ResultScreen(
        state = FakeData.result.copy(me = ResultFighter("Rohan", 33), opponent = ResultFighter("Aman", 71)),
        onShare = {}, onRematch = {},
    )
}
