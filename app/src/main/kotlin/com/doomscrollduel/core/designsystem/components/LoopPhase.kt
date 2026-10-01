package com.doomscrollduel.core.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import com.doomscrollduel.core.designsystem.theme.DuelTheme

/**
 * A 0..1 looping phase for decorative animation. When the user turned animations off it is a
 * constant 0, so everything driven by it renders still. Read `.value` inside draw/graphicsLayer
 * lambdas to avoid recomposing every frame.
 */
@Composable
internal fun rememberLoopPhase(durationMillis: Int, label: String = "loopPhase"): State<Float> {
    if (DuelTheme.motion.reduced) return remember { mutableFloatStateOf(0f) }
    return rememberInfiniteTransition(label = label).animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing), RepeatMode.Restart),
        label = label,
    )
}
