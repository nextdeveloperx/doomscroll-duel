package com.doomscrollduel.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the 4.5:1 text contrast rule (WCAG AA) for every pairing the components use. */
class DuelColorsContrastTest {
    private val c = DuelDarkColors

    private fun ratio(a: Color, b: Color): Float {
        val l1 = maxOf(a.luminance(), b.luminance())
        val l2 = minOf(a.luminance(), b.luminance())
        return (l1 + 0.05f) / (l2 + 0.05f)
    }

    private fun assertAa(name: String, fg: Color, bg: Color) {
        val r = ratio(fg, bg)
        assertTrue("$name contrast was $r, needs 4.5", r >= 4.5f)
    }

    @Test
    fun `light text on dark surfaces`() {
        assertAa("text on background", c.text, c.background)
        assertAa("text on surface", c.text, c.surface)
        assertAa("muted on background", c.textMuted, c.background)
        assertAa("muted on surface", c.textMuted, c.surface)
    }

    @Test
    fun `ink text on every bright fill`() {
        mapOf(
            "yellow" to c.yellow,
            "pink" to c.pink,
            "cyan" to c.cyan,
            "green" to c.green,
            "red" to c.red,
            "orange" to c.orange,
            "orangeDeep" to c.orangeDeep,
            "lavender" to c.lavender,
            "zombie" to c.zombie,
        ).forEach { (name, fill) -> assertAa("ink on $name", c.onBright, fill) }
    }

    @Test
    fun `tag pills`() {
        assertAa("ink on white (FREE)", c.onBright, c.text)
        assertAa("yellow on ink (PRO)", c.yellow, c.outline)
    }

    @Test
    fun `chip and stat text`() {
        assertAa("white on surface chip", c.text, c.surface)
        assertAa("ink on yellow selected chip", c.onBright, c.yellow)
        assertAa("muted tab label on surface", c.textMuted, c.surface)
    }
}
