package com.doomscrollduel.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doomscrollduel.core.designsystem.theme.DuelTheme

/** Round yellow "VS" sticker, tilted a little, with a small hard shadow. */
@Composable
fun VsBadge(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val colors = DuelTheme.colors
    ChunkySurface(
        shape = CircleShape,
        fill = colors.yellow,
        shadowDepth = 3.dp,
        modifier = modifier.rotate(-8f),
    ) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            DuelText(
                text = "VS",
                style = DuelTheme.typography.button.copy(fontSize = (size.value * 0.4f).sp),
                color = colors.onBright,
                maxLines = 1,
            )
        }
    }
}

@Preview(name = "VsBadge", widthDp = 200)
@Composable
private fun VsBadgePreview() = DuelPreview { VsBadge() }
