package com.doomscrollduel.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.doomscrollduel.R
import com.doomscrollduel.core.designsystem.brain.BrainOwner
import com.doomscrollduel.core.designsystem.brain.BrainState
import com.doomscrollduel.core.designsystem.brain.Brain3D
import com.doomscrollduel.core.designsystem.brain.Brain3DMode
import com.doomscrollduel.core.designsystem.brain.BrainView
import com.doomscrollduel.core.designsystem.theme.DuelTheme

/**
 * The hero of the app: the pink brain and the blue brain squaring up across a lightning crack, in front of a glowing
 * sun. All of it is drawn in code (the two brains are [BrainView] in its fighting stance), so it scales, animates and
 * follows the theme. The crack is fixed; only the brains idle.
 */
@Composable
fun FaceOff(modifier: Modifier = Modifier, aspect: Float = 1.3f, arena: Boolean = false, corner: androidx.compose.ui.unit.Dp = 18.dp) {
    val spoken = stringResource(R.string.ds_faceoff_description)
    Brain3D(
        mode = Brain3DMode.FACE_OFF,
        state = BrainState.HAPPY,
        owner = BrainOwner.YOU,
        contentDescription = spoken,
        modifier = modifier.fillMaxWidth().aspectRatio(aspect).clip(RoundedCornerShape(corner)),
        arena = arena,
        fallback = { FaceOffVector(Modifier.fillMaxWidth().aspectRatio(aspect), aspect) },
    )
}

/** The drawn (non-WebGL) version, used if the 3D page cannot load. */
@Composable
private fun FaceOffVector(modifier: Modifier, aspect: Float) {
    val spoken = stringResource(R.string.ds_faceoff_description)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .semantics {
                contentDescription = spoken
                role = Role.Image
            },
    ) {
        val brainWidth = maxWidth * 0.45f
        Canvas(Modifier.matchParentSize()) { drawBackdrop() }
        Row(
            modifier = Modifier.matchParentSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                BrainView(
                    BrainState.HAPPY,
                    owner = BrainOwner.YOU,
                    fighter = true,
                    modifier = Modifier.width(brainWidth).offset(x = -brainWidth * 0.03f),
                )
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                BrainView(
                    BrainState.HAPPY,
                    owner = BrainOwner.OPPONENT,
                    fighter = true,
                    mirrored = true,
                    modifier = Modifier.width(brainWidth).offset(x = brainWidth * 0.03f),
                )
            }
        }
        // The crack goes in front, so the brains look like they are pushing against it.
        Canvas(Modifier.matchParentSize()) { drawCrack() }
    }
}

private fun DrawScope.drawBackdrop() {
    val centre = Offset(size.width / 2f, size.height * 0.5f)
    val radius = size.minDimension * 0.46f
    // Outer bloom, then the sun itself with a hot centre.
    drawCircle(
        Brush.radialGradient(
            0f to Color(0xFFFFD93D).copy(alpha = 0.45f),
            1f to Color.Transparent,
            center = centre,
            radius = radius * 1.55f,
        ),
        radius = radius * 1.55f,
        center = centre,
    )
    drawCircle(
        Brush.radialGradient(
            0f to Color(0xFFFFF3A0),
            0.65f to Color(0xFFFFD93D),
            1f to Color(0xFFFFA94D),
            center = centre,
            radius = radius,
        ),
        radius = radius,
        center = centre,
    )
}

private fun DrawScope.drawCrack() {
    val w = size.width
    val h = size.height
    fun p(x: Float, y: Float) = Offset(w * x, h * y)
    val points = listOf(
        p(0.515f, 0.02f), p(0.485f, 0.14f), p(0.525f, 0.25f), p(0.480f, 0.38f),
        p(0.530f, 0.50f), p(0.482f, 0.63f), p(0.522f, 0.76f), p(0.490f, 0.88f), p(0.505f, 1.0f),
    )
    val crack = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    // Glow, then orange fire, then a white-hot core.
    drawPath(crack, Color(0xFFFF8A5B).copy(alpha = 0.35f), style = Stroke(w * 0.07f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(crack, Color(0xFFFF8A5B), style = Stroke(w * 0.028f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(crack, Color(0xFFFFE04D), style = Stroke(w * 0.016f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(crack, Color.White, style = Stroke(w * 0.007f, cap = StrokeCap.Round, join = StrokeJoin.Round))

    // Flying rock chips either side of the crack.
    val chips = listOf(
        Triple(0.43f, 0.20f, 0.018f), Triple(0.57f, 0.30f, 0.014f), Triple(0.44f, 0.48f, 0.012f),
        Triple(0.58f, 0.62f, 0.02f), Triple(0.42f, 0.74f, 0.015f), Triple(0.56f, 0.86f, 0.012f),
    )
    chips.forEach { (x, y, s) ->
        val c = p(x, y)
        val r = w * s
        val chip = Path().apply {
            moveTo(c.x, c.y - r * 1.2f)
            lineTo(c.x + r, c.y - r * 0.2f)
            lineTo(c.x + r * 0.5f, c.y + r)
            lineTo(c.x - r, c.y + r * 0.4f)
            close()
        }
        drawPath(chip, Color(0xFF3A1A12))
        drawPath(chip, Color(0xFFFF8A5B), style = Stroke(w * 0.003f, join = StrokeJoin.Round))
    }
}

@Preview(name = "FaceOff", widthDp = 360, heightDp = 300)
@Composable
private fun FaceOffPreview() = DuelPreview { FaceOff() }
