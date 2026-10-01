package com.doomscrollduel.core.designsystem.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.doomscrollduel.core.designsystem.theme.ChunkyMetrics
import com.doomscrollduel.core.designsystem.theme.DuelTheme

private val TrackWidth = 64.dp
private val TrackHeight = 36.dp
private val ThumbSize = 24.dp
private val ThumbInset = 6.dp
private val SwitchShadow = 4.dp
private val SwitchTravel = 3.dp

/**
 * On/off switch. Green track + thumb on the right = ON; dark track + thumb on the left = OFF,
 * so state is readable without relying on colour. Touch area is at least 48dp tall.
 * [label] is what TalkBack announces ("Notifications, switch, on").
 */
@Composable
fun ToggleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = DuelTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val thumbX by animateDpAsState(
        targetValue = if (checked) TrackWidth - ThumbSize - ThumbInset else ThumbInset,
        animationSpec = DuelTheme.motion.state(),
        label = "toggleThumb",
    )

    Box(
        modifier = modifier
            .sizeIn(minWidth = ChunkyMetrics.MinTouchTarget, minHeight = ChunkyMetrics.MinTouchTarget)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        ChunkySurface(
            shape = DuelTheme.shapes.chip,
            fill = if (checked) colors.green else colors.background,
            pressed = pressed && enabled,
            shadowDepth = SwitchShadow,
            pressTravel = SwitchTravel,
        ) {
            Box(Modifier.size(width = TrackWidth, height = TrackHeight)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset { IntOffset(thumbX.roundToPx(), 0) }
                        .size(ThumbSize)
                        .background(if (checked) colors.text else colors.textMuted, CircleShape)
                        .border(ChunkyMetrics.OutlineWidth, colors.outline, CircleShape),
                )
            }
        }
    }
}

@Preview(name = "ToggleSwitch", widthDp = 360)
@Composable
private fun ToggleSwitchPreview() = DuelPreview {
    var notifications by remember { mutableStateOf(true) }
    var strict by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        ToggleSwitch(checked = notifications, onCheckedChange = { notifications = it }, label = "Notifications")
        ToggleSwitch(checked = strict, onCheckedChange = { strict = it }, label = "Strict mode")
        ToggleSwitch(checked = true, onCheckedChange = {}, label = "Disabled", enabled = false)
    }
}
