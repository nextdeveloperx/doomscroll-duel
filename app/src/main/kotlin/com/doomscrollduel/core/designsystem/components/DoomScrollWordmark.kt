package com.doomscrollduel.core.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import com.doomscrollduel.R

/** The colours of the logo and the splash: near-black, red, bone white. */
object DoomColors {
    val Ink = Color(0xFF070304)
    val InkLift = Color(0xFF120709)
    val Crimson = Color(0xFFFF2D3F)
    val Ember = Color(0xFFB80F25)
    val Bone = Color(0xFFF7F3F2)
    val Ash = Color(0xFFB4A5A8)
}

/**
 * The app name exactly as the logo draws it (cut from the icon artwork, so the lettering, the infinity "oo" and the
 * 3D shading are the real ones). Always read out by the screen reader as part of its screen; the picture itself is decoration.
 */
@Composable
fun DoomScrollWordmark(height: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.doom_wordmark),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.height(height).clearAndSetSemantics { },
    )
}
