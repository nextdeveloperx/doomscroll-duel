package com.doomscrollduel.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.doomscrollduel.core.designsystem.theme.ChunkyMetrics
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import kotlin.math.PI
import kotlin.math.sin

/**
 * Small outlined text pill: time left, LIVE, permission status, brain state.
 * Always carries a text label, so its meaning never depends on colour alone.
 * [pulsingDot] adds a blinking dot (still when the user turned animations off).
 */
@Composable
fun StatusPill(
    text: String,
    fill: Color,
    modifier: Modifier = Modifier,
    textColor: Color = DuelTheme.colors.onBright,
    pulsingDot: Boolean = false,
) {
    val chip = DuelTheme.shapes.chip
    Row(
        modifier = modifier
            .clip(chip)
            .background(fill)
            .border(ChunkyMetrics.OutlineWidth, DuelTheme.colors.outline, chip)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (pulsingDot) PulsingDot(textColor)
        DuelText(text = text, style = DuelTheme.typography.captionStrong, color = textColor, maxLines = 1)
    }
}

@Composable
private fun PulsingDot(color: Color) {
    val phase = rememberLoopPhase(durationMillis = 1200, label = "liveDot")
    Spacer(
        Modifier
            .size(8.dp)
            .graphicsLayer { alpha = 0.65f + 0.35f * sin(phase.value * 2f * PI.toFloat()) }
            .background(color, CircleShape),
    )
}

@Preview(name = "StatusPill", widthDp = 360)
@Composable
private fun StatusPillPreview() = DuelPreview {
    val colors = DuelTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusPill("LIVE", colors.red, pulsingDot = true)
        StatusPill("3h 20m baaki", colors.yellow)
        StatusPill("CHALU HAI", colors.green)
        StatusPill("BAND HAI", colors.red)
    }
}
