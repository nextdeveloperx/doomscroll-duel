package com.doomscrollduel.core.designsystem.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doomscrollduel.core.designsystem.theme.DuelTheme

/** Big Lilita One number or headline with a blur-free shadow straight below it. */
@Composable
fun HardShadowText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 96.sp,
    color: Color = DuelTheme.colors.text,
    shadowColor: Color = DuelTheme.colors.outline,
    shadowDepth: Dp = 6.dp,
    textAlign: TextAlign = TextAlign.Center,
    maxLines: Int = 1,
) {
    val depthPx = with(LocalDensity.current) { shadowDepth.toPx() }
    val style = DuelTheme.typography.display.copy(
        fontSize = fontSize,
        lineHeight = fontSize * 1.05f,
        shadow = Shadow(color = shadowColor, offset = Offset(0f, depthPx), blurRadius = 0f),
    )
    DuelText(
        text = text,
        // Reserve the shadow's height so it is never clipped by a parent.
        modifier = modifier.padding(bottom = shadowDepth),
        style = style,
        color = color,
        textAlign = textAlign,
        maxLines = maxLines,
    )
}

@Preview(name = "HardShadowText", widthDp = 360)
@Composable
private fun HardShadowTextPreview() = DuelPreview {
    HardShadowText("47")
    HardShadowText("AMAN JEETA!", fontSize = 44.sp, color = DuelTheme.colors.yellow)
}
