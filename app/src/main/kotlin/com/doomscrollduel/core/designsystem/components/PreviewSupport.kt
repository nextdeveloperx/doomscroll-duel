package com.doomscrollduel.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.doomscrollduel.core.designsystem.theme.DuelBackdrop
import com.doomscrollduel.core.designsystem.theme.DuelTheme

/** Wraps previews in the theme on the purple ground. */
@Composable
internal fun DuelPreview(content: @Composable () -> Unit) {
    DuelTheme {
        DuelBackdrop {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) { content() }
        }
    }
}

/** Full-bleed frame for screen previews (screens bring their own background and padding). */
@Composable
internal fun ScreenPreview(content: @Composable () -> Unit) {
    DuelTheme { content() }
}

/** Screen preview with the bottom tab bar, as it appears in the app. */
@Composable
internal fun TabbedScreenPreview(selected: DuelTab, content: @Composable () -> Unit) {
    DuelTheme {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { content() }
            DuelTabBar(selected = selected, onSelect = {})
        }
    }
}
