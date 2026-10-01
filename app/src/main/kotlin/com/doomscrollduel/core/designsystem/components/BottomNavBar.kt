package com.doomscrollduel.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.doomscrollduel.core.designsystem.theme.ChunkyMetrics
import com.doomscrollduel.core.designsystem.theme.DuelTheme

@Immutable
data class BottomNavItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    /** What TalkBack says for the tab. Defaults to the visible label. */
    val contentDescription: String = label,
)

/**
 * Bottom navigation: surface bar with an outlined, rounded top edge. The selected tab is a yellow
 * outlined pill; unselected tabs show icon + label in muted text. Labels are always visible and
 * every tab is at least 48dp tall. Draws under the system navigation bar and pads for it.
 */
@Composable
fun BottomNavBar(
    items: List<BottomNavItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DuelTheme.colors
    val outerShape = DuelTheme.shapes.navBar
    val innerShape = RoundedCornerShape(topStart = 21.dp, topEnd = 21.dp)
    val stroke = ChunkyMetrics.OutlineWidth

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(outerShape)
            .background(colors.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = stroke, start = stroke, end = stroke)
                .clip(innerShape)
                .background(colors.surface)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                NavTab(item = item, selected = item.id == selectedId, onClick = { onSelect(item.id) })
            }
        }
    }
}

@Composable
private fun RowScope.NavTab(item: BottomNavItem, selected: Boolean, onClick: () -> Unit) {
    val colors = DuelTheme.colors
    val chip = DuelTheme.shapes.chip
    val contentColor: Color = if (selected) colors.onBright else colors.textMuted
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .weight(1f)
            .sizeIn(minHeight = ChunkyMetrics.MinTouchTarget)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics { contentDescription = item.contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        // Same border width in both states so the layout does not jump when the pill appears.
        Column(
            modifier = Modifier
                .clearAndSetSemantics { }
                .clip(chip)
                .background(if (selected) colors.yellow else Color.Transparent)
                .border(ChunkyMetrics.OutlineWidth, if (selected) colors.outline else Color.Transparent, chip)
                .padding(horizontal = 14.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DuelIcon(item.icon, tint = contentColor, contentDescription = null, modifier = Modifier.size(24.dp))
            DuelText(
                text = item.label,
                style = DuelTheme.typography.captionStrong,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(name = "BottomNavBar", widthDp = 360)
@Composable
private fun BottomNavBarPreview() = DuelPreview {
    val items = listOf(
        BottomNavItem("home", "Home", DuelIcons.Home),
        BottomNavItem("duel", "Duel", DuelIcons.Bolt),
        BottomNavItem("friends", "Dost", DuelIcons.Friends),
        BottomNavItem("stats", "Stats", DuelIcons.Stats),
    )
    var selected by remember { mutableStateOf("home") }
    BottomNavBar(items = items, selectedId = selected, onSelect = { selected = it })
}
