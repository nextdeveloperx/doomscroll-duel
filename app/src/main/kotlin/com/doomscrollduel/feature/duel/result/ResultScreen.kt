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
import com.doomscrollduel.core.designsystem.components.ChunkyButton
import com.doomscrollduel.core.designsystem.components.ChunkyButtonStyle
import com.doomscrollduel.core.designsystem.components.ChunkyCard
import com.doomscrollduel.core.designsystem.components.DuelIcon
import com.doomscrollduel.core.designsystem.components.DuelIcons
import com.doomscrollduel.core.designsystem.components.DuelScreen
import com.doomscrollduel.core.designsystem.components.DuelText
import com.doomscrollduel.core.designsystem.components.HardShadowText
import com.doomscrollduel.core.designsystem.components.ScreenPreview
import com.doomscrollduel.core.designsystem.components.StatusPill
import com.doomscrollduel.core.designsystem.components.rememberLoopPhase
import com.doomscrollduel.core.designsystem.components.scaled
import com.doomscrollduel.core.designsystem.theme.DuelColors
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import com.doomscrollduel.domain.usecase.DuelOutcome
import com.doomscrollduel.domain.usecase.ResolveDuel
import com.doomscrollduel.feature.FakeData
import kotlin.math.PI
import kotlin.random.Random

@Immutable
data class ResultFighter(val name: String, val reels: Int)

@Immutable
data class ResultUiState(
    val me: ResultFighter,
    val opponent: ResultFighter,
    val reelLimit: Int,
    val stakeCoins: Int,
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
    val colors = DuelTheme.colors
    val outcome = state.outcome
    val headline = when (outcome) {
        DuelOutcome.WIN -> stringResource(R.string.result_winner_you)
        DuelOutcome.LOSS -> stringResource(R.string.result_winner_other, state.opponent.name.uppercase())
        DuelOutcome.DRAW -> stringResource(R.string.result_draw)
    }
    val myState = brainStateOf(state.me.reels, state.reelLimit)
    val opponentState = brainStateOf(state.opponent.reels, state.reelLimit)

    DuelScreen(
        modifier = modifier,
        backdrop = { Confetti(Modifier.fillMaxSize()) },
        bottom = {
            ChunkyButton(
                text = stringResource(R.string.result_share),
                onClick = onShare,
                modifier = Modifier.fillMaxWidth(),
            )
            ChunkyButton(
                text = stringResource(R.string.result_rematch),
                onClick = onRematch,
                style = ChunkyButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        Spacer(Modifier.height(20.dp))
        HardShadowText(
            text = headline,
            fontSize = 52.sp.scaled(),
            color = colors.yellow,
            maxLines = 2,
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
                .width(200.dp.scaled())
                .align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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
        Spacer(Modifier.height(16.dp))
        CoinsCard(outcome = outcome, coins = state.stakeCoins, colors = colors)
        Spacer(Modifier.height(8.dp))
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
    val colors = DuelTheme.colors
    val nameColor = if (owner == BrainOwner.YOU) colors.pink else colors.cyan
    val (stateLabel, stateFill) = when (brain) {
        BrainState.HAPPY -> stringResource(R.string.result_state_happy) to colors.green
        BrainState.FRIED -> stringResource(R.string.result_state_fried) to colors.orange
        BrainState.ZOMBIE -> stringResource(R.string.result_state_zombie) to colors.red
    }
    ChunkyCard(modifier = modifier, contentPadding = PaddingValues(12.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DuelText(text = name, style = DuelTheme.typography.bodyStrong, color = nameColor, maxLines = 1)
            BrainView(state = brain, owner = owner, modifier = Modifier.width(84.dp.scaled()))
            HardShadowText(text = reels.toString(), fontSize = 44.sp.scaled(), shadowDepth = 4.dp)
            DuelText(
                text = stringResource(R.string.result_reels, reels),
                style = DuelTheme.typography.caption,
                color = colors.textMuted,
            )
            StatusPill(text = stateLabel, fill = stateFill)
        }
    }
}

@Composable
private fun CoinsCard(outcome: DuelOutcome, coins: Int, colors: DuelColors) {
    val (text, fill) = when (outcome) {
        DuelOutcome.WIN -> stringResource(R.string.result_coins_won, coins) to colors.green
        DuelOutcome.LOSS -> stringResource(R.string.result_coins_lost, coins) to colors.red
        DuelOutcome.DRAW -> stringResource(R.string.result_coins_draw) to colors.lavender
    }
    ChunkyCard(modifier = Modifier.fillMaxWidth(), fill = fill) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            DuelIcon(DuelIcons.Coin, tint = colors.onBright, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            DuelText(
                text = text,
                style = DuelTheme.typography.heading,
                color = colors.onBright,
                textAlign = TextAlign.Center,
            )
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
 * Decorative falling shapes. Whole-number fall and spin cycles make the loop seamless; with
 * animations turned off the pieces simply stay where they are.
 */
@Composable
private fun BoxScope.Confetti(modifier: Modifier = Modifier) {
    val colors = DuelTheme.colors
    val palette = remember(colors) {
        listOf(colors.yellow, colors.pink, colors.cyan, colors.green, colors.orange, colors.lavender)
    }
    val pieces = remember {
        val random = Random(7)
        List(34) {
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
            val color: Color = palette[piece.colorIndex].copy(alpha = 0.9f)
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

@Preview(name = "Result - draw 390x844", widthDp = 390, heightDp = 844)
@Composable
private fun ResultDrawPreview() = ScreenPreview {
    ResultScreen(
        state = FakeData.result.copy(me = ResultFighter("Rohan", 50), opponent = ResultFighter("Aman", 50)),
        onShare = {}, onRematch = {},
    )
}

@Preview(name = "Result small 320x640", widthDp = 320, heightDp = 640)
@Composable
private fun ResultSmallPreview() = ScreenPreview {
    ResultScreen(state = FakeData.result, onShare = {}, onRematch = {})
}
