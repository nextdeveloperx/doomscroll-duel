package com.doomscrollduel.feature.settings.neon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Thin outline icons for the Settings screen, drawn as 24 x 24 vectors in white so the screen can tint them.
 * Round caps and joins and a 1.8 stroke give the soft, glassy look of the design.
 */
object NeonIcons {
    private fun outline(name: String, width: Float = 1.8f, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, true, true, cx + r, cy)
        arcTo(r, r, 0f, true, true, cx - r, cy)
    }

    val Back: ImageVector by lazy {
        outline("Back", 2.2f) { moveTo(20f, 12f); horizontalLineTo(5f); moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f) }
    }

    val Check: ImageVector by lazy { outline("Check", 2.6f) { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) } }

    val ArrowRight: ImageVector by lazy {
        outline("ArrowRight", 2.2f) { moveTo(5f, 12f); horizontalLineTo(19f); moveTo(13f, 6f); lineTo(19f, 12f); lineTo(13f, 18f) }
    }

    val ChevronRight: ImageVector by lazy { outline("ChevronRight", 2.2f) { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) } }

    /** A shield with a tick: Protection Active. */
    val ShieldCheck: ImageVector by lazy {
        outline("ShieldCheck", 1.6f) {
            moveTo(12f, 2.8f)
            lineTo(19.5f, 5.8f)
            verticalLineTo(11f)
            curveTo(19.5f, 16.2f, 16.4f, 19.8f, 12f, 21.4f)
            curveTo(7.6f, 19.8f, 4.5f, 16.2f, 4.5f, 11f)
            verticalLineTo(5.8f)
            close()
            moveTo(8.4f, 11.8f); lineTo(11f, 14.4f); lineTo(15.8f, 9.2f)
        }
    }

    /** Three rising bars: the on-screen counter. */
    val Bars: ImageVector by lazy {
        outline("Bars", 3.2f) { moveTo(6f, 19f); verticalLineTo(14f); moveTo(12f, 19f); verticalLineTo(9f); moveTo(18f, 19f); verticalLineTo(4.5f) }
    }

    /** A clapperboard with a play triangle: reels. */
    val Reels: ImageVector by lazy {
        outline("Reels", 1.7f) {
            moveTo(4f, 9.5f); horizontalLineTo(20f); verticalLineTo(19f); horizontalLineTo(4f); close()
            moveTo(4f, 5f); horizontalLineTo(20f); verticalLineTo(9.5f); horizontalLineTo(4f); close()
            moveTo(8.5f, 5f); lineTo(6.5f, 9.5f); moveTo(13f, 5f); lineTo(11f, 9.5f); moveTo(17.5f, 5f); lineTo(15.5f, 9.5f)
            moveTo(10f, 12.2f); lineTo(14.6f, 14.3f); lineTo(10f, 16.4f); close()
        }
    }

    val Lock: ImageVector by lazy {
        outline("Lock", 1.8f) {
            moveTo(6f, 11f); horizontalLineTo(18f); verticalLineTo(20f); horizontalLineTo(6f); close()
            moveTo(8.2f, 11f); verticalLineTo(8f); arcTo(3.8f, 3.8f, 0f, false, true, 15.8f, 8f); verticalLineTo(11f)
            moveTo(12f, 14.4f); verticalLineTo(16.8f)
        }
    }

    val People: ImageVector by lazy {
        outline("People", 1.7f) {
            circle(9f, 8f, 3f)
            moveTo(3.2f, 19.5f); curveTo(3.2f, 16.2f, 5.8f, 14.4f, 9f, 14.4f); curveTo(12.2f, 14.4f, 14.8f, 16.2f, 14.8f, 19.5f)
            circle(17.2f, 8.8f, 2.4f)
            moveTo(16.2f, 14.3f); curveTo(18.9f, 14.1f, 20.8f, 15.7f, 20.8f, 18.6f)
        }
    }

    val Hourglass: ImageVector by lazy {
        outline("Hourglass", 1.8f) {
            moveTo(6.5f, 3.8f); horizontalLineTo(17.5f); moveTo(6.5f, 20.2f); horizontalLineTo(17.5f)
            moveTo(7.8f, 3.8f); curveTo(7.8f, 8.8f, 11.2f, 10.2f, 12f, 12f); curveTo(12.8f, 10.2f, 16.2f, 8.8f, 16.2f, 3.8f)
            moveTo(7.8f, 20.2f); curveTo(7.8f, 15.2f, 11.2f, 13.8f, 12f, 12f); curveTo(12.8f, 13.8f, 16.2f, 15.2f, 16.2f, 20.2f)
        }
    }

    val Clock: ImageVector by lazy {
        outline("Clock", 1.8f) { circle(12f, 12f, 9f); moveTo(12f, 6.8f); verticalLineTo(12f); lineTo(15.6f, 14.2f) }
    }

    val Moon: ImageVector by lazy {
        outline("Moon", 1.8f) {
            moveTo(20f, 14.5f)
            curveTo(18.8f, 15.1f, 17.5f, 15.4f, 16f, 15.4f)
            curveTo(11.9f, 15.4f, 8.6f, 12.1f, 8.6f, 8f)
            curveTo(8.6f, 6.5f, 8.9f, 5.2f, 9.5f, 4f)
            curveTo(6.4f, 5f, 4f, 8f, 4f, 11.5f)
            curveTo(4f, 16f, 7.7f, 19.7f, 12.2f, 19.7f)
            curveTo(15.7f, 19.7f, 18.8f, 17.6f, 20f, 14.5f)
            close()
        }
    }

    val Crown: ImageVector by lazy {
        outline("Crown", 1.7f) {
            moveTo(4.2f, 8f); lineTo(8.2f, 12f); lineTo(12f, 5.8f); lineTo(15.8f, 12f); lineTo(19.8f, 8f); lineTo(18.4f, 18f); horizontalLineTo(5.6f); close()
        }
    }

    val Gear: ImageVector by lazy {
        outline("Gear", 1.7f) {
            circle(12f, 12f, 3.2f)
            moveTo(12f, 2.8f); lineTo(13.7f, 4.6f); lineTo(16.2f, 4.2f); lineTo(17.2f, 6.6f); lineTo(19.6f, 7.6f); lineTo(19.2f, 10.1f)
            lineTo(21f, 12f); lineTo(19.2f, 13.9f); lineTo(19.6f, 16.4f); lineTo(17.2f, 17.4f); lineTo(16.2f, 19.8f); lineTo(13.7f, 19.4f)
            lineTo(12f, 21.2f); lineTo(10.3f, 19.4f); lineTo(7.8f, 19.8f); lineTo(6.8f, 17.4f); lineTo(4.4f, 16.4f); lineTo(4.8f, 13.9f)
            lineTo(3f, 12f); lineTo(4.8f, 10.1f); lineTo(4.4f, 7.6f); lineTo(6.8f, 6.6f); lineTo(7.8f, 4.2f); lineTo(10.3f, 4.6f); close()
        }
    }

    val Flame: ImageVector by lazy {
        outline("Flame", 1.7f) {
            moveTo(12f, 3f)
            curveTo(12f, 3f, 6.2f, 8.6f, 6.2f, 14.2f)
            curveTo(6.2f, 17.8f, 8.8f, 20.6f, 12f, 20.6f)
            curveTo(15.2f, 20.6f, 17.8f, 17.8f, 17.8f, 14.2f)
            curveTo(17.8f, 11.6f, 16.4f, 9.6f, 15.2f, 8.2f)
            curveTo(14.9f, 10.2f, 13.9f, 11.1f, 13f, 11.1f)
            curveTo(13f, 8.8f, 13f, 5.6f, 12f, 3f)
            close()
        }
    }

    val Shield: ImageVector by lazy {
        outline("Shield", 1.8f) {
            moveTo(12f, 2.8f); lineTo(19.5f, 5.8f); verticalLineTo(11f)
            curveTo(19.5f, 16.2f, 16.4f, 19.8f, 12f, 21.4f); curveTo(7.6f, 19.8f, 4.5f, 16.2f, 4.5f, 11f); verticalLineTo(5.8f); close()
        }
    }

    val Bell: ImageVector by lazy {
        outline("Bell", 1.8f) {
            moveTo(6f, 16.5f); curveTo(7.2f, 15.2f, 7.5f, 14f, 7.5f, 11f); curveTo(7.5f, 8.2f, 9.4f, 6.4f, 12f, 6.4f)
            curveTo(14.6f, 6.4f, 16.5f, 8.2f, 16.5f, 11f); curveTo(16.5f, 14f, 16.8f, 15.2f, 18f, 16.5f); close()
            moveTo(10.3f, 19.6f); curveTo(10.7f, 20.5f, 11.3f, 20.9f, 12f, 20.9f); curveTo(12.7f, 20.9f, 13.3f, 20.5f, 13.7f, 19.6f)
            moveTo(12f, 4.2f); verticalLineTo(6.4f)
        }
    }

    val Person: ImageVector by lazy {
        outline("Person", 1.8f) {
            circle(12f, 8f, 3.6f)
            moveTo(4.8f, 20f); curveTo(4.8f, 15.8f, 8f, 14f, 12f, 14f); curveTo(16f, 14f, 19.2f, 15.8f, 19.2f, 20f)
        }
    }

    private fun solid(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White), pathBuilder = block)
        }.build()

    val Bolt: ImageVector by lazy {
        solid("Bolt") { moveTo(13.5f, 2f); lineTo(5f, 13.6f); horizontalLineTo(10.8f); lineTo(9.8f, 22f); lineTo(19f, 10f); horizontalLineTo(13f); close() }
    }

    val HomeSolid: ImageVector by lazy {
        solid("HomeSolid") {
            moveTo(12f, 3f); lineTo(2.8f, 11f); horizontalLineTo(5.5f); verticalLineTo(20.5f); horizontalLineTo(10f); verticalLineTo(14.5f)
            horizontalLineTo(14f); verticalLineTo(20.5f); horizontalLineTo(18.5f); verticalLineTo(11f); horizontalLineTo(21.2f); close()
        }
    }

    val PersonSolid: ImageVector by lazy {
        solid("PersonSolid") {
            circle(12f, 7.4f, 4.2f)
            moveTo(4.2f, 21f); curveTo(4.2f, 16.2f, 7.8f, 13.6f, 12f, 13.6f); curveTo(16.2f, 13.6f, 19.8f, 16.2f, 19.8f, 21f); close()
        }
    }

    val Plus: ImageVector by lazy { outline("Plus", 2.6f) { moveTo(12f, 5f); verticalLineTo(19f); moveTo(5f, 12f); horizontalLineTo(19f) } }

    val Coin: ImageVector by lazy { outline("Coin", 1.9f) { circle(12f, 12f, 9f); circle(12f, 12f, 5.2f) } }

    val Swords: ImageVector by lazy {
        outline("Swords", 2.4f) {
            moveTo(4.5f, 4f); lineTo(16f, 15.5f); moveTo(19.5f, 4f); lineTo(8f, 15.5f)
            moveTo(13.4f, 17.6f); lineTo(17.6f, 13.4f); moveTo(10.6f, 17.6f); lineTo(6.4f, 13.4f)
            moveTo(16f, 15.5f); lineTo(19.8f, 19.3f); moveTo(8f, 15.5f); lineTo(4.2f, 19.3f)
        }
    }

    val Gift: ImageVector by lazy {
        outline("Gift", 1.8f) {
            moveTo(4f, 11f); horizontalLineTo(20f); verticalLineTo(20.5f); horizontalLineTo(4f); close()
            moveTo(3f, 7.5f); horizontalLineTo(21f); verticalLineTo(11f); horizontalLineTo(3f); close()
            moveTo(12f, 7.5f); verticalLineTo(20.5f)
            moveTo(12f, 7.5f); curveTo(10.4f, 3.6f, 6.2f, 4.4f, 8f, 7.5f)
            moveTo(12f, 7.5f); curveTo(13.6f, 3.6f, 17.8f, 4.4f, 16f, 7.5f)
        }
    }

    val Store: ImageVector by lazy {
        outline("Store", 1.8f) {
            moveTo(3.5f, 9f); lineTo(5f, 4.5f); horizontalLineTo(19f); lineTo(20.5f, 9f); close()
            moveTo(5f, 12f); verticalLineTo(20.5f); horizontalLineTo(19f); verticalLineTo(12f)
            moveTo(10f, 20.5f); verticalLineTo(15f); horizontalLineTo(14f); verticalLineTo(20.5f)
        }
    }

    val Target: ImageVector by lazy {
        outline("Target", 1.8f) {
            circle(11f, 13f, 8f); circle(11f, 13f, 4.2f)
            moveTo(11f, 13f); lineTo(20f, 4f); moveTo(16.5f, 4f); horizontalLineTo(20f); verticalLineTo(7.5f)
        }
    }

    val Trophy: ImageVector by lazy {
        outline("Trophy", 1.8f) {
            moveTo(7f, 4f); horizontalLineTo(17f); verticalLineTo(10f); curveTo(17f, 12.8f, 14.8f, 14.5f, 12f, 14.5f); curveTo(9.2f, 14.5f, 7f, 12.8f, 7f, 10f); close()
            moveTo(7f, 6f); horizontalLineTo(4.4f); curveTo(4.4f, 9f, 5.4f, 10f, 7.4f, 10.6f)
            moveTo(17f, 6f); horizontalLineTo(19.6f); curveTo(19.6f, 9f, 18.6f, 10f, 16.6f, 10.6f)
            moveTo(12f, 14.5f); verticalLineTo(18f); moveTo(8.5f, 20.5f); horizontalLineTo(15.5f); moveTo(9.2f, 18f); horizontalLineTo(14.8f)
        }
    }

    val Heart: ImageVector by lazy {
        outline("Heart", 1.8f) {
            moveTo(12f, 20f); curveTo(5f, 15f, 3.5f, 11f, 3.5f, 8.5f); curveTo(3.5f, 6f, 5.4f, 4.5f, 7.5f, 4.5f); curveTo(9.5f, 4.5f, 11f, 5.6f, 12f, 7.2f)
            curveTo(13f, 5.6f, 14.5f, 4.5f, 16.5f, 4.5f); curveTo(18.6f, 4.5f, 20.5f, 6f, 20.5f, 8.5f); curveTo(20.5f, 11f, 19f, 15f, 12f, 20f); close()
        }
    }
}
