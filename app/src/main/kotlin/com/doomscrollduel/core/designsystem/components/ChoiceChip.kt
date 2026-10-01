package com.doomscrollduel.core.designsystem.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.doomscrollduel.core.designsystem.theme.ChunkyMetrics
import com.doomscrollduel.core.designsystem.theme.DuelTheme

private val ChipShadow = 4.dp
private val ChipTravel = 3.dp

/**
 * Selectable pill (duration, apps, stake...). Selected = yellow with a check mark, unselected = surface.
 * The check mark means selection is not conveyed by colour alone.
 *
 * Use [Role.RadioButton] (default) for single choice, [Role.Checkbox] for multi choice.
 * Unlike display-only [StatChip], this is a control, so it gets a smaller hard shadow and press travel.
 */
@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: Role = Role.RadioButton,
) {
    val colors = DuelTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val contentColor = if (selected) colors.onBright else colors.text

    ChunkySurface(
        shape = DuelTheme.shapes.chip,
        fill = if (selected) colors.yellow else colors.surface,
        pressed = pressed && enabled,
        shadowDepth = ChipShadow,
        pressTravel = ChipTravel,
        contentAlignment = Alignment.Center,
        modifier = modifier
            .sizeIn(minWidth = ChunkyMetrics.MinTouchTarget, minHeight = ChunkyMetrics.MinTouchTarget)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = role,
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (selected) {
                // Decorative: the selected state is exposed through selectable() semantics.
                DuelIcon(DuelIcons.Check, tint = contentColor, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            DuelText(text = text, style = DuelTheme.typography.bodyStrong, color = contentColor, maxLines = 1)
        }
    }
}

@Preview(name = "ChoiceChip", widthDp = 360)
@Composable
private fun ChoiceChipPreview() = DuelPreview {
    var selectedIndex by remember { mutableStateOf(1) }
    val options = listOf("24 ghante", "3 din", "7 din")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        options.forEachIndexed { index, label ->
            ChoiceChip(text = label, selected = selectedIndex == index, onClick = { selectedIndex = index })
        }
    }
}
