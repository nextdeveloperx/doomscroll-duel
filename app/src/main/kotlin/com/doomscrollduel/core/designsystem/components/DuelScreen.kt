package com.doomscrollduel.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.doomscrollduel.core.designsystem.theme.DuelTheme

/** Screens are designed at 390dp wide. [LocalScreenScale] is the actual width divided by that. */
val LocalScreenScale = staticCompositionLocalOf { 1f }

private val DesignWidth = 390.dp
private val MaxContentWidth = 520.dp
private const val MinScale = 0.85f
private const val MaxScale = 1.25f

/** Scales a design-time size (taken at 390dp wide) to the current screen width. */
@Composable
fun Dp.scaled(): Dp = this * LocalScreenScale.current

/** Same for text sizes that must grow and shrink with the layout (big numbers). */
@Composable
fun TextUnit.scaled(): TextUnit = this * LocalScreenScale.current

/**
 * Standard screen frame: purple ground, status-bar padding, 20dp gutters, scrolling content and an
 * optional pinned [bottom] area (main action buttons) that stays on screen on short phones.
 *
 * - Content is centred and capped at 520dp so tablets and foldables do not stretch it.
 * - Scale (0.85 to 1.25) is provided through [LocalScreenScale] for [scaled] sizes.
 * - Navigation-bar insets are applied under the pinned area; hosts that draw their own bottom bar
 *   should `consumeWindowInsets(WindowInsets.navigationBars)` so they are not applied twice.
 * - [backdrop] is drawn above the ground and below the content (confetti etc.).
 */
@Composable
fun DuelScreen(
    modifier: Modifier = Modifier,
    backdrop: (@Composable BoxScope.() -> Unit)? = null,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(DuelTheme.colors.background)
            // Depth: a violet glow from the top and a pink bloom lower down, over the flat ground.
            .background(
                Brush.radialGradient(
                    colors = listOf(DuelTheme.colors.lavender.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(x = 0.15f * 1000f, y = 0f),
                    radius = 900f,
                ),
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(DuelTheme.colors.pink.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(x = 1000f, y = 1900f),
                    radius = 1100f,
                ),
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        val scale = (minOf(maxWidth, MaxContentWidth) / DesignWidth).coerceIn(MinScale, MaxScale)
        backdrop?.invoke(this)
        CompositionLocalProvider(LocalScreenScale provides scale) {
            Column(
                modifier = Modifier
                    .widthIn(max = MaxContentWidth)
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    content = content,
                )
                if (bottom != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        content = bottom,
                    )
                }
            }
        }
    }
}
