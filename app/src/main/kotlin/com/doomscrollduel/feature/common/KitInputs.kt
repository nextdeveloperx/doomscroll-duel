package com.doomscrollduel.feature.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doomscrollduel.core.designsystem.theme.Nunito
import com.doomscrollduel.feature.settings.neon.NText
import com.doomscrollduel.feature.settings.neon.Neon

/** A labelled single-line text field in the page style. */
@Composable
fun KitField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    enabled: Boolean = true,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    error: Boolean = false,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.fillMaxWidth()) {
        NText(label, 13.sp, color = Neon.VioletLight, weight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(capitalization = capitalization),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && hint != null) NText(hint, 16.sp, color = Neon.Muted.copy(alpha = 0.7f), maxLines = 1)
                    inner()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .clip(shape)
                .background(Kit.Surface)
                .border(1.dp, if (error) Kit.Red else Kit.Violet.copy(alpha = 0.55f), shape)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

/** A round initial on a gradient, for people. */
@Composable
fun KitAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 44.dp, tone: Color = Kit.Blue) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(tone, Kit.VioletDeep))),
        contentAlignment = Alignment.Center,
    ) { NText(name.trim().firstOrNull()?.uppercase() ?: "?", (size.value * 0.42f).sp, weight = FontWeight.ExtraBold) }
}

/** A hairline between rows inside one card. */
@Composable
fun KitDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Kit.Edge))
}

/** Shown wherever a feature needs a username and the signed-in person has not chosen one yet. */
@Composable
fun KitUsernamePrompt(title: String, body: String, button: String, onChoose: () -> Unit, modifier: Modifier = Modifier) {
    KitCard(
        modifier = modifier,
        fill = Brush.horizontalGradient(listOf(Kit.Gold.copy(alpha = 0.22f), Kit.Surface)),
        edge = Kit.Gold.copy(alpha = 0.45f),
    ) {
        NText(title, 17.sp, weight = FontWeight.ExtraBold)
        Spacer(Modifier.height(6.dp))
        NText(body, 14.sp, color = Neon.Muted, lineHeight = 20.sp)
        Spacer(Modifier.height(12.dp))
        KitButton(button, onChoose)
    }
}
