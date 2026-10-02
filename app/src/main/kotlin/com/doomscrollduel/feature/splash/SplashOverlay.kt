package com.doomscrollduel.feature.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doomscrollduel.R
import com.doomscrollduel.core.designsystem.brain.Brain3DReady
import com.doomscrollduel.core.designsystem.components.DoomColors
import com.doomscrollduel.core.designsystem.components.DoomScrollWordmark
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import com.doomscrollduel.feature.settings.neon.NText
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Remembers, for this process, that the opening splash has already been shown (a rotation or a return must not replay it). */
object SplashGate {
    var shown = false
}

private const val MIN_SHOWN_MS = 1700L
private const val MAX_SHOWN_MS = 3600L
private const val FADE_MS = 380

/**
 * The opening screen, in the colours of the app icon: near-black with a red glow.
 *
 * One hero and one loader, nothing else moves: the phone-and-film-ribbon artwork from the icon swings slowly around its
 * vertical axis in 3D (a perspective turn with a small tilt and bob), and a thin red bar runs under the name while the app
 * gets ready. The name is the logo's own lettering. The red glow behind only breathes.
 *
 * It stays at least [MIN_SHOWN_MS] so it reads as a moment and not a flash, and lifts as soon as the 3D brain on the page
 * underneath has loaded, or after [MAX_SHOWN_MS] at the latest, so a slow phone is never stuck here.
 * With "Remove animations" on, nothing moves and the screen only fades.
 */
@Composable
fun SplashOverlay(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val calm = DuelTheme.motion.reduced
    val fade = remember { Animatable(1f) }
    // Entrance, once: the artwork rises out of the dark with a little scale; the name follows a beat later.
    val enter = remember { Animatable(if (calm) 1f else 0f) }
    val nameIn = remember { Animatable(if (calm) 1f else 0f) }

    LaunchedEffect(Unit) {
        val begin = System.currentTimeMillis()
        withTimeoutOrNull(MAX_SHOWN_MS) { Brain3DReady.isReady.first { it } }
        val shownFor = System.currentTimeMillis() - begin
        if (shownFor < MIN_SHOWN_MS) delay(MIN_SHOWN_MS - shownFor)
        fade.animateTo(0f, tween(FADE_MS, easing = LinearEasing))
        SplashGate.shown = true
        onFinished()
    }
    LaunchedEffect(Unit) { if (!calm) enter.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
    LaunchedEffect(Unit) {
        if (!calm) {
            delay(380)
            nameIn.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
        }
    }

    val infinite = rememberInfiniteTransition(label = "splash")
    val swing by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart), label = "swing",
    )
    val breathe by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe",
    )
    val sweep by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "sweep",
    )

    val twoPi = 2f * PI.toFloat()
    val turn = if (calm) 0f else sin(swing * twoPi) * 18f
    val tilt = if (calm) 0f else sin((swing + 0.25f) * twoPi) * 5f
    val bob = if (calm) 0f else sin(swing * twoPi * 2f) * 5f
    val title = stringResource(R.string.splash_title)
    val tagline = stringResource(R.string.splash_tagline)

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(fade.value)
            .semantics(mergeDescendants = true) { contentDescription = "$title. $tagline" },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(DoomColors.InkLift, DoomColors.Ink)))
            drawRect(
                Brush.radialGradient(
                    listOf(DoomColors.Crimson.copy(alpha = 0.26f + 0.10f * breathe), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.38f), radius = size.width * 0.95f,
                ),
            )
            drawRect(
                Brush.radialGradient(
                    listOf(DoomColors.Ember.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 1.02f), radius = size.width * 0.9f,
                ),
            )
        }
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Image(
                painter = painterResource(R.drawable.splash_art),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .graphicsLayer {
                        cameraDistance = 14f * density
                        rotationY = turn
                        rotationX = tilt
                        translationY = bob.dp.toPx() - (1f - enter.value) * 36.dp.toPx()
                        val s = 0.6f + 0.4f * enter.value
                        scaleX = s
                        scaleY = s
                        alpha = enter.value
                    },
            )
            Spacer(Modifier.height(6.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    alpha = nameIn.value
                    translationY = (1f - nameIn.value) * 14.dp.toPx()
                },
            ) {
                DoomScrollWordmark(height = 64.dp)
                Spacer(Modifier.height(4.dp))
                NText(tagline, 15.sp, color = DoomColors.Ash, weight = FontWeight.SemiBold, align = TextAlign.Center)
            }
            Spacer(Modifier.weight(1f))
            // A thin bar of light running along a track: the app is getting ready.
            Box(Modifier.width(120.dp).height(4.dp).clearAndSetSemantics { }) {
                Canvas(Modifier.fillMaxSize()) {
                    drawLine(Color.White.copy(alpha = 0.10f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = size.height, cap = StrokeCap.Round)
                    val w = size.width * 0.34f
                    val x = if (calm) (size.width - w) / 2f else (size.width - w) * sweep
                    drawLine(
                        Brush.horizontalGradient(listOf(DoomColors.Ember, DoomColors.Crimson), startX = x, endX = x + w),
                        Offset(x, size.height / 2), Offset(x + w, size.height / 2), strokeWidth = size.height, cap = StrokeCap.Round,
                    )
                }
            }
            Spacer(Modifier.height(64.dp))
        }
    }
}
