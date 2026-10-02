package com.doomscrollduel.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.doomscrollduel.R

/** The heavy display face of the Battle banner (Russo One, SIL OFL). */
val RussoOne = FontFamily(Font(R.font.russo_one, FontWeight.Normal))

/** Headings, buttons and big numbers. Bundled, so it renders offline. */
val LilitaOne = FontFamily(Font(R.font.lilita_one, FontWeight.Normal))

/**
 * Body text. One variable font file serves both weights (variable fonts need API 26+, which is our min SDK).
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Nunito = FontFamily(
    Font(
        resId = R.font.nunito_variable,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(FontWeight.SemiBold.weight)),
    ),
    Font(
        resId = R.font.nunito_variable,
        weight = FontWeight.ExtraBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(FontWeight.ExtraBold.weight)),
    ),
)

/**
 * Type scale: display 68, title 32, heading 24, button 22, body 16, caption 13.
 * Colours are intentionally unset; components pick them so contrast stays under their control.
 */
@Immutable
class DuelTypography(
    val display: TextStyle = TextStyle(
        fontFamily = LilitaOne,
        fontWeight = FontWeight.Normal,
        fontSize = 68.sp,
        lineHeight = 72.sp,
    ),
    val title: TextStyle = TextStyle(
        fontFamily = LilitaOne,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 38.sp,
    ),
    val heading: TextStyle = TextStyle(
        fontFamily = LilitaOne,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    val button: TextStyle = TextStyle(
        fontFamily = LilitaOne,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 26.sp,
    ),
    val body: TextStyle = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    val bodyStrong: TextStyle = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    val caption: TextStyle = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    val captionStrong: TextStyle = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
)
