package com.doomscrollduel.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.doomscrollduel.R
import com.doomscrollduel.core.designsystem.theme.DuelTheme

enum class StepperKind { Decrement, Increment }

/**
 * Square +/- button (56dp including its hard shadow, so the touch area is above 48dp).
 * [contentDescription] is required: the glyph is drawn, not text.
 */
@Composable
fun StepperButton(
    kind: StepperKind,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = DuelTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val glyphColor = if (enabled) colors.onBright else colors.textMuted

    ChunkySurface(
        shape = DuelTheme.shapes.button,
        fill = if (enabled) colors.yellow else colors.surface,
        pressed = pressed && enabled,
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(56.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
    ) {
        Canvas(Modifier.size(20.dp)) {
            val stroke = 5.dp.toPx()
            val half = stroke / 2f
            val mid = size.width / 2f
            drawLine(glyphColor, Offset(half, mid), Offset(size.width - half, mid), stroke, StrokeCap.Round)
            if (kind == StepperKind.Increment) {
                drawLine(glyphColor, Offset(mid, half), Offset(mid, size.height - half), stroke, StrokeCap.Round)
            }
        }
    }
}

/** [-] value [+] row, e.g. for choosing a reel limit. The value is announced politely when it changes. */
@Composable
fun Stepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange,
    modifier: Modifier = Modifier,
    step: Int = 1,
    valueText: (Int) -> String = { it.toString() },
    decreaseDescription: String = stringResource(R.string.ds_stepper_decrease),
    increaseDescription: String = stringResource(R.string.ds_stepper_increase),
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StepperButton(
            kind = StepperKind.Decrement,
            onClick = { onValueChange((value - step).coerceAtLeast(valueRange.first)) },
            contentDescription = decreaseDescription,
            enabled = value > valueRange.first,
        )
        DuelText(
            text = valueText(value),
            style = DuelTheme.typography.heading,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .widthIn(min = 72.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        StepperButton(
            kind = StepperKind.Increment,
            onClick = { onValueChange((value + step).coerceAtMost(valueRange.last)) },
            contentDescription = increaseDescription,
            enabled = value < valueRange.last,
        )
    }
}

@Preview(name = "Stepper", widthDp = 360)
@Composable
private fun StepperPreview() = DuelPreview {
    var limit by remember { mutableIntStateOf(100) }
    Stepper(value = limit, onValueChange = { limit = it }, valueRange = 10..500, step = 10)
    StepperButton(StepperKind.Increment, onClick = {}, contentDescription = "Badhao", enabled = false)
}
