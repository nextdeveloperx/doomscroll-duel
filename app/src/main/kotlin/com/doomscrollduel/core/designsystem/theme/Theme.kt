package com.doomscrollduel.core.designsystem.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/**
 * Motion policy. When the user turned animations off in system settings
 * ("Remove animations" / animator scale 0) every spec collapses to a [snap]
 * and decorative loops (brain wobble etc.) stop.
 */
@Immutable
class DuelMotion(val reduced: Boolean) {
    /** Press/release travel of chunky elements. No overshoot, so the hard shadow never inverts. */
    fun <T> press(): AnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)

    /** Value changes: HP bar, toggle thumb, colour shifts. */
    fun <T> state(): AnimationSpec<T> = if (reduced) snap() else tween(durationMillis = 220)
}

internal val LocalDuelColors = staticCompositionLocalOf { DuelDarkColors }
internal val LocalDuelTypography = staticCompositionLocalOf { DuelTypography() }
internal val LocalDuelShapes = staticCompositionLocalOf { DuelShapes() }
internal val LocalDuelMotion = staticCompositionLocalOf { DuelMotion(reduced = false) }

object DuelTheme {
    val colors: DuelColors
        @Composable @ReadOnlyComposable get() = LocalDuelColors.current
    val typography: DuelTypography
        @Composable @ReadOnlyComposable get() = LocalDuelTypography.current
    val shapes: DuelShapes
        @Composable @ReadOnlyComposable get() = LocalDuelShapes.current
    val motion: DuelMotion
        @Composable @ReadOnlyComposable get() = LocalDuelMotion.current
}

/** Provides tokens. Does not paint anything; wrap screens in [DuelBackdrop] for the purple ground. */
@Composable
fun DuelTheme(
    colors: DuelColors = DuelDarkColors,
    reducedMotion: Boolean = rememberSystemReducedMotion(),
    content: @Composable () -> Unit,
) {
    val typography = remember { DuelTypography() }
    val shapes = remember { DuelShapes() }
    val motion = remember(reducedMotion) { DuelMotion(reducedMotion) }
    CompositionLocalProvider(
        LocalDuelColors provides colors,
        LocalDuelTypography provides typography,
        LocalDuelShapes provides shapes,
        LocalDuelMotion provides motion,
        content = content,
    )
}

/** The deep purple ground every screen sits on. */
@Composable
fun DuelBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.background(DuelTheme.colors.background)) { content() }
}

/** True when the system animator duration scale is 0 ("Remove animations"). Updates live. */
@Composable
fun rememberSystemReducedMotion(): Boolean {
    val context = LocalContext.current
    var reduced by remember { mutableStateOf(readReducedMotion { context.contentResolver }) }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = readReducedMotion { resolver }
            }
        }
        runCatching {
            resolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                observer,
            )
        }
        onDispose { runCatching { resolver.unregisterContentObserver(observer) } }
    }
    return reduced
}

private inline fun readReducedMotion(resolver: () -> android.content.ContentResolver): Boolean =
    runCatching {
        Settings.Global.getFloat(resolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
