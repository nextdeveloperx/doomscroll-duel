package com.doomscrollduel.core.designsystem.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.doomscrollduel.core.designsystem.theme.DuelTheme

/** The only text primitive in the design system (no Material `Text`). */
@Composable
fun DuelText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = DuelTheme.typography.body,
    color: Color = DuelTheme.colors.text,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val resolved = style.copy(color = color).let { if (textAlign != null) it.copy(textAlign = textAlign) else it }
    BasicText(
        text = text,
        modifier = modifier,
        style = resolved,
        overflow = overflow,
        maxLines = maxLines,
    )
}
