package com.doomscrollduel.core.designsystem.brain

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
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
import com.doomscrollduel.core.designsystem.components.rememberLoopPhase
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

// Extra room below the brain for the legs of the fighting stance, and the lobe that carries the face.
private const val FOOTROOM = 44f
private const val FACE_LOBE = 2

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
    /** Fighting stance: legs, fists and a fierce brow. Used for face-offs. */
    fighter: Boolean = false,
    /** Faces left instead of right (only visible in the fighting stance). */
    mirrored: Boolean = false,
    contentDescription: String = brainDescription(state, owner, crowned),
) {
    val colors = DuelTheme.colors
    val bodyTarget = when (state) {
        BrainState.HAPPY -> if (owner == BrainOwner.YOU) colors.pink else colors.cyan
        BrainState.FRIED -> colors.orange
        BrainState.ZOMBIE -> colors.zombie
    }
    val body by animateColorAsState(bodyTarget, DuelTheme.motion.state(), label = "brainBody")
    val phase = rememberLoopPhase(durationMillis = 1600, label = "brainIdle")
    val paths = remember { BrainPaths() }
    val headroom = if (crowned) CROWN_HEADROOM else 0f
    val footroom = if (fighter) FOOTROOM else 0f

    Canvas(
        modifier = modifier
            .aspectRatio(DESIGN_WIDTH / (DESIGN_HEIGHT + headroom + footroom))
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Image
            },
    ) {
        val scale = size.width / DESIGN_WIDTH
        withTransform({
            scale(scale, scale, pivot = Offset.Zero)
            translate(0f, headroom)
            if (mirrored) scale(-1f, 1f, pivot = Offset(DESIGN_WIDTH / 2f, 0f))
        }) {
            drawBrain(state, body, colors, phase.value, crowned, fighter, paths)
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

private fun DrawScope.drawBrain(
    state: BrainState,
    body: Color,
    colors: DuelColors,
    phase: Float,
    crowned: Boolean,
    fighter: Boolean,
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
        // Contact shadow on the ground, so the brain sits on something instead of floating.
        drawOval(Color.Black.copy(alpha = 0.30f), topLeft = Offset(24f, if (fighter) 196f else 158f), size = Size(152f, 14f))

        if (fighter) drawLegs(body, ink, white)

        // Silhouette outline first, then each lobe over the previous one.
        Lobes.forEach { drawCircle(ink, radius = it.r + OUTLINE, center = Offset(it.cx, it.cy)) }

        // Every lobe is a lit sphere: light from the top left, a darker rim bottom right, a cool rim light on the
        // far edge, and (except on the face lobe) a texture of folds. Seams are the body colour darkened, so the
        // lobes read as soft, squashy shapes pressed together.
        val lit = lerp(body, white, 0.46f)
        val shade = lerp(body, ink, 0.42f)
        val seam = lerp(body, ink, 0.62f)
        val rim = lerp(body, colors.lavender, 0.55f)
        Lobes.forEachIndexed { index, it ->
            val centre = Offset(it.cx, it.cy)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to lit,
                    0.45f to body,
                    1f to shade,
                    center = Offset(it.cx - it.r * 0.38f, it.cy - it.r * 0.42f),
                    radius = it.r * 1.6f,
                ),
                radius = it.r,
                center = centre,
            )
            if (index != FACE_LOBE) {
                clipPath(paths.lobeClips[index]) {
                    val folds = paths.folds[index]
                    // The light lip sits just below each dark groove: that is what makes the fold look carved.
                    translate(0f, 1.7f) { drawPath(folds, white.copy(alpha = 0.26f), style = Stroke(2.6f, cap = StrokeCap.Round)) }
                    drawPath(folds, ink.copy(alpha = 0.34f), style = Stroke(3.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            // Cool rim light on the shadow side.
            drawArc(
                color = rim.copy(alpha = 0.55f),
                startAngle = 20f,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = Offset(it.cx - it.r + 2.5f, it.cy - it.r + 2.5f),
                size = Size((it.r - 2.5f) * 2f, (it.r - 2.5f) * 2f),
                style = Stroke(2.6f, cap = StrokeCap.Round),
            )
            // Wet-looking specular highlight.
            drawCircle(
                brush = Brush.radialGradient(
                    0f to white.copy(alpha = 0.75f),
                    1f to Color.Transparent,
                    center = Offset(it.cx - it.r * 0.4f, it.cy - it.r * 0.46f),
                    radius = it.r * 0.42f,
                ),
                radius = it.r,
                center = centre,
            )
            drawCircle(seam, radius = it.r, center = centre, style = Stroke(SEAM))
        }
        drawPath(paths.crease, ink, style = Stroke(4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        when (state) {
            BrainState.HAPPY -> drawHappyFace(ink, white, colors, paths)
            BrainState.FRIED -> drawFriedFace(ink, white, colors, paths, phase)
            BrainState.ZOMBIE -> drawZombieFace(ink, body, paths)
        }
        if (fighter) {
            if (state == BrainState.HAPPY) drawFierceBrows(ink)
            drawArms(body, ink, white)
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
    // Big glossy eye: cream white, brown iris, dark pupil and two catch-lights, like the 3D mascot art.
    val sclera = Offset(x, 100f)
    drawOval(
        Brush.radialGradient(0f to white, 1f to Color(0xFFE8DCCB), center = Offset(x - 3f, 94f), radius = 16f),
        topLeft = Offset(x - 10f, 87f),
        size = Size(20f, 26f),
    )
    drawOval(ink, topLeft = Offset(x - 10f, 87f), size = Size(20f, 26f), style = Stroke(2.2f))
    drawCircle(Color(0xFF4A2C1D), radius = 6.4f, center = sclera.copy(y = 102f))
    drawCircle(ink, radius = 3.4f, center = sclera.copy(y = 102f))
    drawCircle(white, radius = 2.4f, center = Offset(x - 2.2f, 98.5f))
    drawCircle(white.copy(alpha = 0.8f), radius = 1.2f, center = Offset(x + 2.2f, 105f))
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
    /** One circle per lobe, used to keep each lobe's folds inside it. */
    val lobeClips: List<Path> = Lobes.map { lobe ->
        Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(lobe.cx, lobe.cy), lobe.r)) }
    }

    /**
     * The gyri: short, thick, meandering worms like the ridges of a real brain, not a regular pattern. A seeded
     * random walk per lobe, so the brain always looks the same but no two lobes match.
     */
    val folds: List<Path> = Lobes.mapIndexed { lobeIndex, lobe ->
        val rnd = kotlin.random.Random(7 + lobeIndex * 31)
        Path().apply {
            repeat(11) {
                var angle = rnd.nextFloat() * 2f * PI.toFloat()
                val dist = lobe.r * (0.15f + 0.7f * rnd.nextFloat())
                var x = lobe.cx + dist * cos(angle)
                var y = lobe.cy + dist * sin(angle)
                moveTo(x, y)
                var heading = rnd.nextFloat() * 2f * PI.toFloat()
                repeat(4) {
                    // Each step turns a little, alternating left and right, which makes the S-shaped ridges.
                    heading += (if (it % 2 == 0) 1f else -1f) * (0.9f + rnd.nextFloat())
                    val step = lobe.r * (0.22f + 0.16f * rnd.nextFloat())
                    val nx = x + step * cos(heading)
                    val ny = y + step * sin(heading)
                    quadraticBezierTo(x + step * 0.9f * cos(heading + 0.9f), y + step * 0.9f * sin(heading + 0.9f), nx, ny)
                    x = nx
                    y = ny
                }
            }
        }
    }

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

// ----- fighting stance ---------------------------------------------------------------------------------------------

/** Two bent legs with round shoes, drawn behind the brain. Same lighting as the lobes. */
private fun DrawScope.drawLegs(body: Color, ink: Color, white: Color) {
    val lit = lerp(body, white, 0.4f)
    val shade = lerp(body, ink, 0.4f)
    listOf(
        Triple(Offset(70f, 156f), Offset(54f, 196f), -1f),
        Triple(Offset(130f, 156f), Offset(146f, 196f), 1f),
    ).forEach { (hip, foot, side) ->
        val leg = Path().apply {
            moveTo(hip.x, hip.y)
            quadraticBezierTo(hip.x + side * 18f, (hip.y + foot.y) / 2f, foot.x, foot.y)
        }
        drawPath(leg, ink, style = Stroke(21f, cap = StrokeCap.Round))
        drawPath(leg, shade, style = Stroke(14.5f, cap = StrokeCap.Round))
        drawPath(leg, body, style = Stroke(10f, cap = StrokeCap.Round))
        // Shoe
        val shoeCentre = Offset(foot.x + side * 7f, foot.y + 3f)
        drawOval(ink, topLeft = Offset(shoeCentre.x - 19f, shoeCentre.y - 9f), size = Size(38f, 21f))
        drawOval(
            Brush.radialGradient(
                0f to lit,
                1f to shade,
                center = Offset(shoeCentre.x - 5f, shoeCentre.y - 4f),
                radius = 26f,
            ),
            topLeft = Offset(shoeCentre.x - 16.5f, shoeCentre.y - 6.5f),
            size = Size(33f, 16f),
        )
    }
}

/** A raised guard: the near arm bent up in front of the chest with a fist, the far one tucked behind it. */
private fun DrawScope.drawArms(body: Color, ink: Color, white: Color) {
    val lit = lerp(body, white, 0.42f)
    val shade = lerp(body, ink, 0.4f)
    listOf(
        Triple(Offset(150f, 128f), Offset(132f, 152f), 1f),
        Triple(Offset(160f, 108f), Offset(142f, 132f), 0f),
    ).forEach { (shoulder, fist, _) ->
        val arm = Path().apply {
            moveTo(shoulder.x, shoulder.y)
            quadraticBezierTo(shoulder.x + 18f, (shoulder.y + fist.y) / 2f + 6f, fist.x, fist.y)
        }
        drawPath(arm, ink, style = Stroke(18f, cap = StrokeCap.Round))
        drawPath(arm, body, style = Stroke(11.5f, cap = StrokeCap.Round))
        drawCircle(ink, radius = 12.5f, center = fist)
        drawCircle(
            Brush.radialGradient(
                0f to lit,
                0.6f to body,
                1f to shade,
                center = Offset(fist.x - 4f, fist.y - 4f),
                radius = 17f,
            ),
            radius = 10f,
            center = fist,
        )
        drawCircle(white.copy(alpha = 0.7f), radius = 2.6f, center = Offset(fist.x - 3.5f, fist.y - 4f))
    }
}

/** Two heavy brows slanting in towards the nose: the "I will beat you" look. */
private fun DrawScope.drawFierceBrows(ink: Color) {
    val brown = Color(0xFF3A2218)
    drawLine(ink, Offset(66f, 80f), Offset(91f, 90f), strokeWidth = 8.5f, cap = StrokeCap.Round)
    drawLine(brown, Offset(66f, 80f), Offset(91f, 90f), strokeWidth = 5.5f, cap = StrokeCap.Round)
    drawLine(ink, Offset(134f, 80f), Offset(109f, 90f), strokeWidth = 8.5f, cap = StrokeCap.Round)
    drawLine(brown, Offset(134f, 80f), Offset(109f, 90f), strokeWidth = 5.5f, cap = StrokeCap.Round)
}
