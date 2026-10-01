package com.doomscrollduel.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Corner radii: cards 24, buttons 20, tiles 24, inputs 16, chips fully round. */
@Immutable
class DuelShapes(
    val card: Shape = RoundedCornerShape(24.dp),
    val button: Shape = RoundedCornerShape(20.dp),
    val tile: Shape = RoundedCornerShape(24.dp),
    val input: Shape = RoundedCornerShape(16.dp),
    val chip: Shape = RoundedCornerShape(percent = 50),
    /** Top edge of the bottom navigation bar. */
    val navBar: Shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
)

/** Fixed geometry shared by every chunky element. */
object ChunkyMetrics {
    val OutlineWidth = 3.dp
    val ShadowDepth = 6.dp
    val PressTravel = 4.dp
    val PressedShadow = 1.dp

    /** Minimum interactive size (Android accessibility guideline). */
    val MinTouchTarget = 48.dp

    /** Height of a button face, excluding its hard shadow. */
    val ButtonFaceHeight = 56.dp
}
