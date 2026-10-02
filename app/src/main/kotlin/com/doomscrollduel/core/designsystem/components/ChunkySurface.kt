package com.doomscrollduel.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.lerp
import com.doomscrollduel.core.designsystem.theme.ChunkyMetrics
import com.doomscrollduel.core.designsystem.theme.DuelTheme

/**
 * The chunky look in one place: flat [fill], 3dp ink outline, and a hard (blur-free) shadow
 * [shadowDepth] below the face.
 *
 * Layout: the surface is [shadowDepth] taller than its face, so the shadow never gets clipped and
 * size constraints from the caller (min touch size, fillMaxHeight) include it.
 * Press: when [pressed] the face drops [pressTravel] and the visible shadow shrinks to [pressedShadow].
 * Input handling (click, toggle, select) is the caller's job; attach it to [modifier] so the touch
 * area is the stable outer bounds rather than the moving face.
 */
@Composable
internal fun ChunkySurface(
    shape: Shape,
    fill: Color,
    modifier: Modifier = Modifier,
    pressed: Boolean = false,
    shadowDepth: Dp = ChunkyMetrics.ShadowDepth,
    pressTravel: Dp = ChunkyMetrics.PressTravel,
    pressedShadow: Dp = ChunkyMetrics.PressedShadow,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val ink = DuelTheme.colors.outline
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = DuelTheme.motion.press(),
        label = "chunkyPress",
    )
    val measurePolicy = remember(shadowDepth) { ChunkyMeasurePolicy(shadowDepth) }

    Layout(
        content = {
            Box(
                modifier = Modifier
                    .graphicsLayer { translationY = pressTravel.toPx() * pressProgress.coerceIn(0f, 1f) }
                    .clip(shape)
                    .background(fill)
                    // Soft studio light: a highlight along the top and a little shade at the bottom, so faces feel
                    // like raised, glossy pieces instead of flat stickers. Kept faint so text contrast is unchanged.
                    .background(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.16f),
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.10f),
                        ),
                    )
                    .border(ChunkyMetrics.OutlineWidth, ink, shape),
                contentAlignment = contentAlignment,
                content = content,
            )
        },
        modifier = modifier.drawBehind {
            val progress = pressProgress.coerceIn(0f, 1f)
            val depthPx = shadowDepth.toPx()
            val faceSize = Size(size.width, size.height - depthPx)
            val travelPx = pressTravel.toPx() * progress
            val shadowTop = travelPx + lerp(depthPx, pressedShadow.toPx(), progress)
            val outline = shape.createOutline(faceSize, layoutDirection, this)
            translate(top = shadowTop) { drawOutline(outline, ink) }
        },
        measurePolicy = measurePolicy,
    )
}

private class ChunkyMeasurePolicy(private val depth: Dp) : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val depthPx = depth.roundToPx()
        val inner = constraints.copy(
            minHeight = (constraints.minHeight - depthPx).coerceAtLeast(0),
            maxHeight = if (constraints.hasBoundedHeight) {
                (constraints.maxHeight - depthPx).coerceAtLeast(0)
            } else {
                Constraints.Infinity
            },
        )
        val face = measurables.first().measure(inner)
        return layout(face.width, face.height + depthPx) { face.place(0, 0) }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables.first().minIntrinsicWidth((height - depth.roundToPx()).coerceAtLeast(0))

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables.first().maxIntrinsicWidth((height - depth.roundToPx()).coerceAtLeast(0))

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        measurables.first().minIntrinsicHeight(width) + depth.roundToPx()

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        measurables.first().maxIntrinsicHeight(width) + depth.roundToPx()
}
