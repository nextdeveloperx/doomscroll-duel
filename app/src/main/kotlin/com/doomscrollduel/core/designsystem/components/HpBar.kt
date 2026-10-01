package com.doomscrollduel.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.doomscrollduel.R
import com.doomscrollduel.core.designsystem.theme.ChunkyMetrics
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import kotlin.math.max
import kotlin.math.min

/**
 * Health bar for the brain. [hp] is 0..100 (see `ReelUsage.hpForPercentUsed`).
 * The numeric label always sits outside the bar, so colour is never the only signal and the
 * label keeps full contrast whatever the fill level is.
 */
@Composable
fun HpBar(
    hp: Int,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    val colors = DuelTheme.colors
    val safeHp = hp.coerceIn(0, 100)
    val fraction by animateFloatAsState(
        targetValue = safeHp / 100f,
        animationSpec = DuelTheme.motion.state(),
        label = "hpFraction",
    )
    val fillColor = when {
        safeHp > 60 -> colors.green
        safeHp > 25 -> colors.orange
        else -> colors.red
    }
    val description = stringResource(R.string.ds_hp_description, safeHp)
    val chip = DuelTheme.shapes.chip

    Row(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = description
            progressBarRangeInfo = ProgressBarRangeInfo(safeHp.toFloat(), 0f..100f)
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .clip(chip)
                .background(colors.background)
                .drawBehind {
                    val stroke = ChunkyMetrics.OutlineWidth.toPx()
                    val innerHeight = size.height - 2 * stroke
                    val fillWidth = (size.width - 2 * stroke) * fraction.coerceIn(0f, 1f)
                    if (fillWidth > 0f) {
                        drawRoundRect(
                            color = fillColor,
                            topLeft = Offset(stroke, stroke),
                            size = Size(fillWidth, innerHeight),
                            cornerRadius = CornerRadius(min(innerHeight, fillWidth) / 2f),
                        )
                        if (fillWidth > innerHeight) {
                            // Flat highlight stripe for the game look.
                            drawRoundRect(
                                color = colors.text.copy(alpha = 0.3f),
                                topLeft = Offset(stroke + innerHeight * 0.3f, stroke + innerHeight * 0.15f),
                                size = Size(max(fillWidth - innerHeight * 0.6f, 0f), innerHeight * 0.22f),
                                cornerRadius = CornerRadius(innerHeight * 0.11f),
                            )
                        }
                    }
                }
                .border(ChunkyMetrics.OutlineWidth, colors.outline, chip),
        )
        if (showLabel) {
            DuelText(
                text = stringResource(R.string.ds_hp_label, safeHp),
                style = DuelTheme.typography.button,
                color = colors.text,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.widthIn(min = 72.dp),
            )
        }
    }
}

@Preview(name = "HpBar", widthDp = 360)
@Composable
private fun HpBarPreview() = DuelPreview {
    listOf(100, 72, 40, 12, 0).forEach { HpBar(hp = it, modifier = Modifier.fillMaxWidth()) }
}
