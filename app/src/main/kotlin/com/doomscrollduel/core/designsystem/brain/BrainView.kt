package com.doomscrollduel.core.designsystem.brain

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.doomscrollduel.R
import com.doomscrollduel.core.designsystem.components.DuelPreview
import com.doomscrollduel.core.designsystem.theme.DuelColors
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

// The brain is drawn in a 200 x 170 design space and scaled to the width it is given.
private const val DESIGN_WIDTH = 200f
private const val DESIGN_HEIGHT = 170f

// Extra headroom above the brain when a crown is shown.
private const val CROWN_HEADROOM = 30f

private const val OUTLINE = 5f // visible outer outline, in design units
private const val SEAM = 3.5f // seams where lobes overlap

private class Lobe(val cx: Float, val cy: Float, val r: Float)

// Five overlapping round lobes, back to front.
private val Lobes = arrayOf(
    Lobe(42f, 100f, 34f), // middle left
    Lobe(158f, 100f, 34f), // middle right
    Lobe(100f, 112f, 50f), // bottom centre (carries the face)
    Lobe(64f, 54f, 40f), // top left
    Lobe(136f, 54f, 40f), // top right
)

/**
 * The cartoon brain, drawn purely with vector shapes (no images).
 *
 * - [BrainState.HAPPY]: pink, smiling. (Cyan for the opponent.)
 * - [BrainState.FRIED]: orange, crack on top, spiral eyes, sweat drop, wobbly mouth.
 * - [BrainState.ZOMBIE]: green, X eyes, wavy mouth, stitched scar.
 *
 * Idle animation (breathing, wobble, spinning eyes, sweat drop) is skipped entirely when the user
 * has "Remove animations" on. The size follows the width you give it; height keeps the aspect ratio.
 */
@Composable
fun BrainView(
    state: BrainState,
    modifier: Modifier = Modifier,
    owner: BrainOwner = BrainOwner.YOU,
    crowned: Boolean = false,
    contentDescription: String = brainDescription(state, owner, crowned),
) {
    val colors = DuelTheme.colors
    val bodyTarget = when (state) {
        BrainState.HAPPY -> if (owner == BrainOwner.YOU) colors.pink else colors.cyan
        BrainState.FRIED -> colors.orange
        BrainState.ZOMBIE -> colors.zombie
    }
    val body by animateColorAsState(bodyTarget, DuelTheme.motion.state(), label = "brainBody")
    val phase = rememberBrainPhase()
    val paths = remember { BrainPaths() }
    val headroom = if (crowned) CROWN_HEADROOM else 0f

    Canvas(
        modifier = modifier
            .aspectRatio(DESIGN_WIDTH / (DESIGN_HEIGHT + headroom))
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Image
            },
    ) {
        val scale = size.width / DESIGN_WIDTH
        withTransform({
            scale(scale, scale, pivot = Offset.Zero)
            translate(0f, headroom)
        }) {
            drawBrain(state, body, colors, phase.value, crowned, paths)
        }
    }
}

@Composable
private fun brainDescription(state: BrainState, owner: BrainOwner, crowned: Boolean): String {
    val base = stringResource(
        when (owner) {
            BrainOwner.YOU -> when (state) {
                BrainState.HAPPY -> R.string.ds_brain_you_happy
                BrainState.FRIED -> R.string.ds_brain_you_fried
                BrainState.ZOMBIE -> R.string.ds_brain_you_zombie
            }
            BrainOwner.OPPONENT -> when (state) {
                BrainState.HAPPY -> R.string.ds_brain_opponent_happy
                BrainState.FRIED -> R.string.ds_brain_opponent_fried
                BrainState.ZOMBIE -> R.string.ds_brain_opponent_zombie
            }
        },
    )
    return if (crowned) stringResource(R.string.ds_brain_winner_suffix, base) else base
}

/** 0..1 looping phase, or a constant 0 when motion is reduced (everything then renders still). */
@Composable
private fun rememberBrainPhase(): State<Float> {
    if (DuelTheme.motion.reduced) return remember { mutableFloatStateOf(0f) }
    return rememberInfiniteTransition(label = "brainIdle").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1600, easing = LinearEasing), RepeatMode.Restart),
        label = "brainPhase",
    )
}

private fun DrawScope.drawBrain(
    state: BrainState,
    body: Color,
    colors: DuelColors,
    phase: Float,
    crowned: Boolean,
    paths: BrainPaths,
) {
    val ink = colors.outline
    val white = colors.text
    val wave = sin(phase * 2f * PI.toFloat())

    val rotation = when (state) {
        BrainState.HAPPY -> 0f
        BrainState.FRIED -> 2.5f * wave
        BrainState.ZOMBIE -> 1.8f * wave
    }
    val scaleX = if (state == BrainState.HAPPY) 1f - 0.012f * wave else 1f
    val scaleY = if (state == BrainState.HAPPY) 1f + 0.025f * wave else 1f

    withTransform({
        scale(scaleX, scaleY, pivot = Offset(100f, 164f))
        rotate(rotation, pivot = Offset(100f, 150f))
    }) {
        // Silhouette outline first, then each lobe over the previous one with a thinner seam.
        Lobes.forEach { drawCircle(ink, radius = it.r + OUTLINE, center = Offset(it.cx, it.cy)) }
        Lobes.forEach {
            val centre = Offset(it.cx, it.cy)
            drawCircle(body, radius = it.r, center = centre)
            drawCircle(ink, radius = it.r, center = centre, style = Stroke(SEAM))
        }

        // Shine, wrinkles and the centre crease.
        drawArc(
            color = white.copy(alpha = 0.35f),
            startAngle = 205f,
            sweepAngle = 50f,
            useCenter = false,
            topLeft = Offset(64f - 29f, 54f - 29f),
            size = Size(58f, 58f),
            style = Stroke(5f, cap = StrokeCap.Round),
        )
        drawPath(paths.wrinkles, ink.copy(alpha = 0.35f), style = Stroke(3f, cap = StrokeCap.Round))
        drawPath(paths.crease, ink, style = Stroke(4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        when (state) {
            BrainState.HAPPY -> drawHappyFace(ink, white, colors, paths)
            BrainState.FRIED -> drawFriedFace(ink, white, colors, paths, phase)
            BrainState.ZOMBIE -> drawZombieFace(ink, body, paths)
        }

        if (crowned) drawCrown(ink, colors, paths)
    }
}

private fun DrawScope.drawHappyFace(ink: Color, white: Color, colors: DuelColors, paths: BrainPaths) {
    drawCircle(colors.red.copy(alpha = 0.35f), radius = 8f, center = Offset(58f, 118f))
    drawCircle(colors.red.copy(alpha = 0.35f), radius = 8f, center = Offset(142f, 118f))
    happyEye(78f, ink, white)
    happyEye(122f, ink, white)
    drawPath(paths.smile, ink, style = Stroke(6f, cap = StrokeCap.Round))
}

private fun DrawScope.happyEye(x: Float, ink: Color, white: Color) {
    drawOval(ink, topLeft = Offset(x - 6.5f, 91f), size = Size(13f, 18f))
    drawCircle(white, radius = 2.6f, center = Offset(x - 1.8f, 95.5f))
}

private fun DrawScope.drawFriedFace(ink: Color, white: Color, colors: DuelColors, paths: BrainPaths, phase: Float) {
    drawPath(paths.crack, ink, style = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    spiralEye(78f, white, ink, paths, phase * 360f)
    spiralEye(122f, white, ink, paths, -phase * 360f)
    drawPath(paths.friedMouth, ink, style = Stroke(6f, cap = StrokeCap.Round, join = StrokeJoin.Round))

    // Sweat drop slides down and fades, then restarts.
    val alpha = (1f - phase.pow(3)).coerceIn(0f, 1f)
    translate(left = 152f, top = 62f + 14f * phase) {
        drawPath(paths.drop, colors.cyan.copy(alpha = alpha))
        drawPath(paths.drop, ink.copy(alpha = alpha), style = Stroke(3f, join = StrokeJoin.Round))
    }
}

private fun DrawScope.spiralEye(x: Float, white: Color, ink: Color, paths: BrainPaths, degrees: Float) {
    val centre = Offset(x, 100f)
    drawCircle(white, radius = 12f, center = centre)
    drawCircle(ink, radius = 12f, center = centre, style = Stroke(3f))
    translate(left = x, top = 100f) {
        rotate(degrees, pivot = Offset.Zero) {
            drawPath(paths.spiral, ink, style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private fun DrawScope.drawZombieFace(ink: Color, body: Color, paths: BrainPaths) {
    // Darker patches of rotten skin.
    val patch = lerp(body, ink, 0.22f)
    drawCircle(patch, radius = 9f, center = Offset(150f, 128f))
    drawCircle(patch, radius = 7f, center = Offset(48f, 84f))
    drawCircle(patch, radius = 5f, center = Offset(112f, 146f))

    // Stitched scar across the top-right lobe.
    drawLine(ink, Offset(118f, 22f), Offset(150f, 46f), strokeWidth = 4f, cap = StrokeCap.Round)
    for (i in 1..3) {
        val t = i / 4f
        val px = 118f + 32f * t
        val py = 22f + 24f * t
        drawLine(ink, Offset(px + 3f, py - 4f), Offset(px - 3f, py + 4f), strokeWidth = 3f, cap = StrokeCap.Round)
    }

    crossEye(78f, ink)
    crossEye(122f, ink)
    drawPath(paths.zombieMouth, ink, style = Stroke(6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.crossEye(x: Float, ink: Color) {
    drawLine(ink, Offset(x - 7f, 93f), Offset(x + 7f, 107f), strokeWidth = 5f, cap = StrokeCap.Round)
    drawLine(ink, Offset(x + 7f, 93f), Offset(x - 7f, 107f), strokeWidth = 5f, cap = StrokeCap.Round)
}

private fun DrawScope.drawCrown(ink: Color, colors: DuelColors, paths: BrainPaths) {
    rotate(-8f, pivot = Offset(100f, 0f)) {
        drawPath(paths.crown, colors.yellow)
        drawPath(paths.crown, ink, style = Stroke(5f, join = StrokeJoin.Round))
        drawCircle(colors.red, radius = 3.5f, center = Offset(84f, 6f))
        drawCircle(colors.cyan, radius = 3.5f, center = Offset(100f, 6f))
        drawCircle(colors.green, radius = 3.5f, center = Offset(116f, 6f))
    }
}

/** Static shapes in design units, built once per BrainView. */
private class BrainPaths {
    val crease = Path().apply {
        moveTo(100f, 14f)
        cubicTo(93f, 28f, 107f, 40f, 100f, 58f)
    }
    val wrinkles = Path().apply {
        moveTo(40f, 44f)
        quadraticBezierTo(50f, 32f, 64f, 38f)
        moveTo(136f, 38f)
        quadraticBezierTo(150f, 32f, 160f, 44f)
        moveTo(22f, 98f)
        quadraticBezierTo(28f, 88f, 40f, 92f)
        moveTo(160f, 92f)
        quadraticBezierTo(172f, 88f, 178f, 98f)
    }
    val smile = Path().apply {
        moveTo(80f, 122f)
        quadraticBezierTo(100f, 142f, 120f, 122f)
    }
    val friedMouth = wavy(x = 76f, y = 128f, segments = 4, width = 12f, amplitude = 7f)
    val zombieMouth = wavy(x = 72f, y = 126f, segments = 4, width = 14f, amplitude = 9f)
    val crack = Path().apply {
        moveTo(96f, 8f)
        lineTo(105f, 22f)
        lineTo(95f, 33f)
        lineTo(104f, 46f)
        lineTo(98f, 56f)
    }
    val spiral = spiral(maxRadius = 8f)
    val drop = Path().apply {
        moveTo(0f, -12f)
        cubicTo(5f, -5f, 8.5f, -0.5f, 8.5f, 4.5f)
        cubicTo(8.5f, 9.5f, 4.8f, 12.5f, 0f, 12.5f)
        cubicTo(-4.8f, 12.5f, -8.5f, 9.5f, -8.5f, 4.5f)
        cubicTo(-8.5f, -0.5f, -5f, -5f, 0f, -12f)
        close()
    }
    val crown = Path().apply {
        moveTo(70f, 14f)
        lineTo(66f, -14f)
        lineTo(84f, -2f)
        lineTo(100f, -20f)
        lineTo(116f, -2f)
        lineTo(134f, -14f)
        lineTo(130f, 14f)
        close()
    }
}

private fun wavy(x: Float, y: Float, segments: Int, width: Float, amplitude: Float): Path = Path().apply {
    moveTo(x, y)
    for (i in 0 until segments) {
        val direction = if (i % 2 == 0) -1f else 1f
        relativeQuadraticBezierTo(width / 2f, direction * amplitude, width, 0f)
    }
}

private fun spiral(maxRadius: Float, turns: Float = 2.5f, steps: Int = 48): Path = Path().apply {
    moveTo(0f, 0f)
    for (i in 1..steps) {
        val f = i / steps.toFloat()
        val angle = f * turns * 2f * PI.toFloat()
        lineTo(maxRadius * f * cos(angle), maxRadius * f * sin(angle))
    }
}

@Preview(name = "BrainView states", widthDp = 420, heightDp = 520)
@Composable
private fun BrainViewPreview() = DuelPreview {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BrainState.entries.forEach { BrainView(state = it, modifier = Modifier.width(120.dp)) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BrainView(BrainState.HAPPY, owner = BrainOwner.OPPONENT, modifier = Modifier.width(120.dp))
        BrainView(BrainState.HAPPY, crowned = true, modifier = Modifier.width(120.dp))
        BrainView(BrainState.ZOMBIE, owner = BrainOwner.OPPONENT, crowned = true, modifier = Modifier.width(120.dp))
    }
}
