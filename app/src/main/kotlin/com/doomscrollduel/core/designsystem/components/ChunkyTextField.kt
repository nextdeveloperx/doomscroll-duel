package com.doomscrollduel.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.doomscrollduel.core.designsystem.theme.ChunkyMetrics
import com.doomscrollduel.core.designsystem.theme.DuelTheme

/**
 * One-line text box in the chunky style: 3dp outline, 16dp corners, at least 48dp tall. [label] is what TalkBack reads
 * (a visible caption should sit above it); [hint] shows only while the box is empty.
 */
@Composable
fun ChunkyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    hint: String = "",
    enabled: Boolean = true,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Characters,
) {
    val colors = DuelTheme.colors
    val shape = DuelTheme.shapes.chip
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = DuelTheme.typography.bodyStrong.copy(color = colors.text),
        cursorBrush = SolidColor(colors.yellow),
        keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = ImeAction.Done),
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ChunkyMetrics.MinTouchTarget)
                    .clip(shape)
                    .background(colors.surface)
                    .border(ChunkyMetrics.OutlineWidth, colors.outline, shape)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty() && hint.isNotEmpty()) {
                    DuelText(text = hint, style = DuelTheme.typography.bodyStrong, color = colors.textMuted)
                }
                inner()
            }
        },
    )
}

@Preview(widthDp = 390)
@Composable
private fun ChunkyTextFieldPreview() = DuelPreview {
    ChunkyTextField(value = "", onValueChange = {}, label = "Confirm", hint = "DELETE")
    ChunkyTextField(value = "DELETE", onValueChange = {}, label = "Confirm")
}
