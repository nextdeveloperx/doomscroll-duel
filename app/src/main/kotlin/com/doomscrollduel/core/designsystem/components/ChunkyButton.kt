package com.doomscrollduel.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.doomscrollduel.core.designsystem.theme.ChunkyMetrics
import com.doomscrollduel.core.designsystem.theme.DuelColors
import com.doomscrollduel.core.designsystem.theme.DuelTheme

enum class ChunkyButtonStyle {
    /** Main action. One per screen. */
    Primary,
    Secondary,
    Success,
    Danger,

    /** Actions that belong to the user ("you" colour). */
    You,

    /** Actions that belong to the opponent. */
    Opponent,
}

private fun ChunkyButtonStyle.fill(colors: DuelColors): Color = when (this) {
    ChunkyButtonStyle.Primary -> colors.yellow
    ChunkyButtonStyle.Secondary -> colors.lavender
    ChunkyButtonStyle.Success -> colors.green
    ChunkyButtonStyle.Danger -> colors.red
    ChunkyButtonStyle.You -> colors.pink
    ChunkyButtonStyle.Opponent -> colors.cyan
}

/**
 * Chunky push button: 3dp outline, 20dp corners, 6dp hard shadow. Pressed: face drops 4dp, shadow 1dp.
 * Label is Lilita One 22 in ink on a bright fill (>= 4.5:1 on every [ChunkyButtonStyle]).
 */
@Composable
fun ChunkyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ChunkyButtonStyle = ChunkyButtonStyle.Primary,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClickLabel: String? = null,
) {
    val colors = DuelTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val fill = if (enabled) style.fill(colors) else colors.surface
    val contentColor = if (enabled) colors.onBright else colors.textMuted

    ChunkySurface(
        shape = DuelTheme.shapes.button,
        fill = fill,
        pressed = pressed && enabled,
        contentAlignment = Alignment.Center,
        modifier = modifier
            .sizeIn(
                minWidth = ChunkyMetrics.MinTouchTarget,
                minHeight = ChunkyMetrics.ButtonFaceHeight + ChunkyMetrics.ShadowDepth,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClickLabel = onClickLabel,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (icon != null) {
                // Decorative: the label next to it already names the action.
                DuelIcon(icon, tint = contentColor, contentDescription = null, modifier = Modifier.size(24.dp))
            }
            DuelText(
                text = text,
                style = DuelTheme.typography.button,
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(name = "ChunkyButton styles", widthDp = 360)
@Composable
private fun ChunkyButtonPreview() = DuelPreview {
    ChunkyButtonStyle.entries.forEach { style ->
        ChunkyButton(text = style.name.uppercase(), onClick = {}, style = style, modifier = Modifier.sizeIn(minWidth = 200.dp))
    }
    ChunkyButton(text = "DUEL SHURU KARO", onClick = {}, icon = DuelIcons.Bolt)
    ChunkyButton(text = "DISABLED", onClick = {}, enabled = false)
}
