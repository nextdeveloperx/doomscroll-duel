package com.doomscrollduel.core.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size

/**
 * Tintable icons drawn as vectors, so the design system ships no icon library.
 * All icons live in a 24 x 24 viewport and are tinted by [DuelIcon].
 */
object DuelIcons {
    val Flame: ImageVector by lazy {
        icon("Flame") {
            moveTo(12f, 2f)
            curveTo(12f, 2f, 5.5f, 8.5f, 5.5f, 14.5f)
            curveTo(5.5f, 18.4f, 8.4f, 21.5f, 12f, 21.5f)
            curveTo(15.6f, 21.5f, 18.5f, 18.4f, 18.5f, 14.5f)
            curveTo(18.5f, 11.5f, 16.8f, 9.3f, 15.5f, 7.8f)
            curveTo(15.2f, 10f, 14f, 11f, 13f, 11f)
            curveTo(13f, 8.5f, 13f, 5f, 12f, 2f)
            close()
        }
    }

    /** Coin: ring plus centre dot. */
    val Coin: ImageVector by lazy {
        icon("Coin", PathFillType.EvenOdd) {
            circle(12f, 12f, 10f)
            circle(12f, 12f, 7f)
            circle(12f, 12f, 3.8f)
        }
    }

    val Home: ImageVector by lazy {
        icon("Home") {
            moveTo(12f, 3f)
            lineTo(2.5f, 11.5f)
            horizontalLineTo(5f)
            verticalLineTo(21f)
            horizontalLineTo(10f)
            verticalLineTo(15f)
            horizontalLineTo(14f)
            verticalLineTo(21f)
            horizontalLineTo(19f)
            verticalLineTo(11.5f)
            horizontalLineTo(21.5f)
            close()
        }
    }

    val Bolt: ImageVector by lazy {
        icon("Bolt") {
            moveTo(13f, 2f)
            lineTo(4f, 14f)
            horizontalLineTo(11f)
            lineTo(10f, 22f)
            lineTo(20f, 9f)
            horizontalLineTo(13f)
            close()
        }
    }

    val Friends: ImageVector by lazy {
        icon("Friends") {
            circle(9f, 8f, 4f)
            moveTo(2f, 20f)
            curveTo(2f, 15.5f, 5f, 14f, 9f, 14f)
            curveTo(13f, 14f, 16f, 15.5f, 16f, 20f)
            close()
            circle(17.5f, 9f, 3f)
            moveTo(17f, 14.2f)
            curveTo(20.5f, 14.2f, 22f, 15.8f, 22f, 19f)
            horizontalLineTo(18f)
            curveTo(18f, 17f, 17.6f, 15.5f, 17f, 14.2f)
            close()
        }
    }

    val Stats: ImageVector by lazy {
        icon("Stats") {
            moveTo(4f, 20f)
            verticalLineTo(12f)
            horizontalLineTo(8f)
            verticalLineTo(20f)
            close()
            moveTo(10f, 20f)
            verticalLineTo(4f)
            horizontalLineTo(14f)
            verticalLineTo(20f)
            close()
            moveTo(16f, 20f)
            verticalLineTo(9f)
            horizontalLineTo(20f)
            verticalLineTo(20f)
            close()
        }
    }

    val Profile: ImageVector by lazy {
        icon("Profile") {
            circle(12f, 8f, 4.5f)
            moveTo(3.5f, 21f)
            curveTo(3.5f, 16.5f, 7f, 14.5f, 12f, 14.5f)
            curveTo(17f, 14.5f, 20.5f, 16.5f, 20.5f, 21f)
            close()
        }
    }

    val Check: ImageVector by lazy {
        icon("Check") {
            moveTo(9.2f, 16.2f)
            lineTo(5.3f, 12.3f)
            lineTo(3.9f, 13.7f)
            lineTo(9.2f, 19f)
            lineTo(20.6f, 7.6f)
            lineTo(19.2f, 6.2f)
            close()
        }
    }
}

private fun icon(
    name: String,
    fillType: PathFillType = PathFillType.NonZero,
    block: PathBuilder.() -> Unit,
): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(fill = SolidColor(Color.Black), pathFillType = fillType, pathBuilder = block).build()

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 2 * r, dy1 = 0f)
    arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = -2 * r, dy1 = 0f)
    close()
}

/**
 * Renders a [DuelIcons] vector in [tint].
 * Pass a [contentDescription] for any icon that stands alone. Pass `null` only when the icon sits
 * next to text that already names it, or when the parent sets merged semantics (all components in
 * this package do the latter).
 */
@Composable
fun DuelIcon(
    icon: ImageVector,
    tint: Color,
    contentDescription: String?,
    modifier: Modifier = Modifier.size(24.dp),
) {
    Image(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = modifier,
        colorFilter = ColorFilter.tint(tint),
    )
}
