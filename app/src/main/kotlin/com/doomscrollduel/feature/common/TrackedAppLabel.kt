package com.doomscrollduel.feature.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.doomscrollduel.R
import com.doomscrollduel.core.designsystem.theme.DuelColors
import com.doomscrollduel.tracking.model.TrackedApp

/** Name shown for each counted surface ("YouTube Shorts", not just "YouTube"). */
@Composable
fun TrackedApp.label(): String = stringResource(
    when (this) {
        TrackedApp.INSTAGRAM -> R.string.app_instagram
        TrackedApp.YOUTUBE -> R.string.app_youtube
        TrackedApp.FACEBOOK -> R.string.app_facebook
        TrackedApp.SNAPCHAT -> R.string.app_snapchat
    },
)

/** Bar colour per app. Colours only decorate; every bar also shows its number. */
fun TrackedApp.accent(colors: DuelColors): Color = when (this) {
    TrackedApp.INSTAGRAM -> colors.pink
    TrackedApp.YOUTUBE -> colors.red
    TrackedApp.FACEBOOK -> colors.cyan
    TrackedApp.SNAPCHAT -> colors.yellow
}
