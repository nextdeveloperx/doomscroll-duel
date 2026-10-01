package com.doomscrollduel.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Doomscroll Duel colour tokens. There is a single (dark purple) scheme on purpose:
 * the look is a game world, not a light/dark-switching utility app.
 *
 * Contrast rules (verified in `DuelColorsContrastTest`):
 *  - [text] and [textMuted] are used on [background] and [surface].
 *  - Every bright fill ([yellow], [pink], [cyan], [green], [red], [orange], [orangeDeep],
 *    [lavender], [zombie]) carries [onBright] (the outline ink) text, never white.
 */
@Immutable
class DuelColors(
    val background: Color = Color(0xFF1B1037),
    val surface: Color = Color(0xFF2A1A57),
    val outline: Color = Color(0xFF0B0620),
    val text: Color = Color(0xFFFFFFFF),
    val textMuted: Color = Color(0xFFC5B8F0),
    /** Main action. */
    val yellow: Color = Color(0xFFFFD93D),
    /** "You". */
    val pink: Color = Color(0xFFFF7AC6),
    /** Opponent. */
    val cyan: Color = Color(0xFF3DE0FF),
    /** Win, success. */
    val green: Color = Color(0xFF4DF0A0),
    /** Danger, limit crossed. */
    val red: Color = Color(0xFFFF4D6D),
    val orange: Color = Color(0xFFFFA94D),
    val orangeDeep: Color = Color(0xFFFF8A5B),
    val lavender: Color = Color(0xFFB49CFF),
    val zombie: Color = Color(0xFF8BDB6A),
) {
    /** Text and icon colour for anything drawn on a bright fill. */
    val onBright: Color get() = outline
}

val DuelDarkColors = DuelColors()
