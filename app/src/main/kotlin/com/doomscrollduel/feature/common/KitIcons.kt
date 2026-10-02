package com.doomscrollduel.feature.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Line icons in the same style as the rest of the app (2dp round strokes on a 24dp grid), tinted by the caller.
 * Most outlines are from Feather (feathericons.com, MIT licence); see licenses/feather-icons.txt.
 */
object KitIcons {
    private fun icon(name: String, vararg outlines: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            outlines.forEach { d ->
                addPath(
                    pathData = PathParser().parsePathString(d).toNodes(),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    /** A dot with radio waves on both sides: "broadcast", clearly not a notification bell. */
    val Broadcast: ImageVector by lazy {
        ImageVector.Builder("Broadcast", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.4f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(12f, 12f)
                lineTo(12.01f, 12f)
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(15.4f, 8.6f); arcTo(4.8f, 4.8f, 0f, false, true, 15.4f, 15.4f)
                moveTo(8.6f, 8.6f); arcTo(4.8f, 4.8f, 0f, false, false, 8.6f, 15.4f)
                moveTo(18.5f, 5.5f); arcTo(9.2f, 9.2f, 0f, false, true, 18.5f, 18.5f)
                moveTo(5.5f, 5.5f); arcTo(9.2f, 9.2f, 0f, false, false, 5.5f, 18.5f)
            }
        }.build()
    }

    val Mic: ImageVector by lazy {
        icon("Mic", "M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z", "M19 10v2a7 7 0 0 1-14 0v-2", "M12 19v4", "M8 23h8")
    }

    val MicOff: ImageVector by lazy {
        icon(
            "MicOff", "M1 1l22 22", "M9 9v3a3 3 0 0 0 5.12 2.12M15 9.34V4a3 3 0 0 0-5.94-.6",
            "M17 16.95A7 7 0 0 1 5 12v-2m14 0v2a7 7 0 0 1-.11 1.23", "M12 19v4", "M8 23h8",
        )
    }

    /** Loudspeaker with waves. */
    val Speaker: ImageVector by lazy {
        icon("Speaker", "M11 5L6 9H2v6h4l5 4V5z", "M19.07 4.93a10 10 0 0 1 0 14.14M15.54 8.46a5 5 0 0 1 0 7.07")
    }

    /** Phone handset: the ear speaker. */
    val Earpiece: ImageVector by lazy {
        icon(
            "Earpiece",
            "M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z",
        )
    }

    /** Hang up: the handset crossed out. */
    val HangUp: ImageVector by lazy {
        icon(
            "HangUp",
            "M10.68 13.31a16 16 0 0 0 3.41 2.6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7 2 2 0 0 1 1.72 2v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.42 19.42 0 0 1-3.33-2.67m-2.67-3.34a19.79 19.79 0 0 1-3.07-8.63A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91",
            "M23 1L1 23",
        )
    }

    /** A person with a plus: invite somebody. */
    val UserPlus: ImageVector by lazy {
        icon(
            "UserPlus", "M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2", "M8.5 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z", "M20 8v6", "M23 11h-6",
        )
    }

    /** A small arrow pointing up; rotate it for "going out" and "coming in". */
    val ArrowUp: ImageVector by lazy { icon("ArrowUp", "M12 19V5", "M5 12l7-7 7 7") }
}
