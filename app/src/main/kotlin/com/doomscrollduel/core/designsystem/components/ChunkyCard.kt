package com.doomscrollduel.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.doomscrollduel.core.designsystem.theme.ChunkyMetrics
import com.doomscrollduel.core.designsystem.theme.DuelTheme

/**
 * Container card: 24dp corners, 3dp outline, 6dp hard shadow.
 * Pass [onClick] to make the whole card a button (it then gets the press animation).
 */
@Composable
fun ChunkyCard(
    modifier: Modifier = Modifier,
    fill: Color = DuelTheme.colors.surface,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val clickable = if (onClick != null) {
        Modifier
            .sizeIn(minWidth = ChunkyMetrics.MinTouchTarget, minHeight = ChunkyMetrics.MinTouchTarget)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = onClickLabel,
                role = Role.Button,
                onClick = onClick,
            )
    } else {
        Modifier
    }

    ChunkySurface(
        shape = DuelTheme.shapes.card,
        fill = fill,
        pressed = pressed && onClick != null,
        modifier = modifier.then(clickable),
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Preview(name = "ChunkyCard", widthDp = 360)
@Composable
private fun ChunkyCardPreview() = DuelPreview {
    ChunkyCard(modifier = Modifier.fillMaxWidth()) {
        DuelText("Aaj ka score", style = DuelTheme.typography.heading)
        DuelText("Tumne 42 reels dekhi. Limit 100 hai.", color = DuelTheme.colors.textMuted)
    }
    ChunkyCard(modifier = Modifier.fillMaxWidth(), onClick = {}) {
        DuelText("Tappable card", style = DuelTheme.typography.heading)
        DuelText("Dabake dekho, neeche jaata hai.", color = DuelTheme.colors.textMuted)
    }
}
