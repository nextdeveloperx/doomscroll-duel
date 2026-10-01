package com.doomscrollduel.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doomscrollduel.core.designsystem.theme.ChunkyMetrics
import com.doomscrollduel.core.designsystem.theme.DuelTheme

/**
 * Initial-letter avatar. Pass [contentDescription] when the avatar stands alone; leave it null when
 * the person's name is shown right next to it (then TalkBack skips the avatar).
 */
@Composable
fun Avatar(
    name: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    contentDescription: String? = null,
) {
    val colors = DuelTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .border(ChunkyMetrics.OutlineWidth, colors.outline, CircleShape)
            .clearAndSetSemantics { contentDescription?.let { this.contentDescription = it } },
        contentAlignment = Alignment.Center,
    ) {
        DuelText(
            text = name.take(1).uppercase(),
            style = DuelTheme.typography.heading.copy(fontSize = (size.value * 0.48f).sp, lineHeight = (size.value * 0.6f).sp),
            color = colors.onBright,
            maxLines = 1,
        )
    }
}

@Preview(name = "Avatar", widthDp = 200)
@Composable
private fun AvatarPreview() = DuelPreview {
    Avatar("Rohan", DuelTheme.colors.pink)
    Avatar("Aman", DuelTheme.colors.cyan, size = 64.dp)
}
