package com.doomscrollduel.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.platform.app.InstrumentationRegistry
import com.doomscrollduel.core.designsystem.theme.DuelTheme

/** A string from the app under test, formatted like the screen formats it. */
fun str(@StringRes id: Int, vararg args: Any): String =
    InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

/** Every screen needs the design system theme around it, exactly as `MainActivity` provides. */
fun ComposeContentTestRule.setDuelContent(content: @Composable () -> Unit) {
    setContent { DuelTheme { content() } }
}
