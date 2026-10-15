package com.doomscrollduel.feature.counter

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doomscrollduel.R
import com.doomscrollduel.core.designsystem.brain.BrainState
import com.doomscrollduel.core.designsystem.brain.BrainView
import com.doomscrollduel.core.designsystem.brain.ReelUsage
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import com.doomscrollduel.feature.settings.neon.NText
import kotlinx.coroutines.delay

/**
 * The pill drawn over Instagram, YouTube, Facebook and Snapchat: today's reels, with our own mascot brain next to
 * the number. Shows only our own number (never anything from the other app) and takes no touches.
 *
 * Visual states (see the class doc on [com.doomscrollduel.tracking.service.ReelCounterBadge] for the window
 * lifecycle around this): full opacity right after a new reel is counted, fading to a faint but still-present
 * [IDLE_ALPHA] once nothing new has landed for [idleTimeoutMillis], and locked back to full opacity for as long as
 * [limit] has been reached or passed — the idle timer never fades a target-reached badge. Reaching zero reels again
 * (a new day) naturally drops out of that state since `count >= limit` stops being true.
 */
@Composable
fun FloatingCounter(
    count: Int,
    limit: Int,
    modifier: Modifier = Modifier,
    idleTimeoutMillis: Long = IDLE_TIMEOUT_MS,
) {
    val targetReached = limit > 0 && count >= limit

    // Restarts cleanly on every count change: LaunchedEffect cancels whatever it was doing for the previous key
    // before starting the new delay, so there is never a stale "go idle" callback left over from an older count.
    var idle by remember { mutableStateOf(false) }
    var lastCountedAt by remember { mutableLongStateOf(count.toLong()) }
    LaunchedEffect(count) {
        lastCountedAt = System.currentTimeMillis()
        idle = false
    }
    LaunchedEffect(lastCountedAt, targetReached) {
        if (targetReached) return@LaunchedEffect // locked fully visible; no idle timer while the target holds
        delay(idleTimeoutMillis)
        idle = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (idle) IDLE_ALPHA else 1f,
        animationSpec = tween(FADE_DURATION_MS),
        label = "counterAlpha",
    )

    val percent = ReelUsage.percentUsed(count, limit)
    val brainState = BrainState.fromPercentUsed(percent)
    val shape = RoundedCornerShape(50)
    val description = stringResource(R.string.counter_badge_description, count, limit)

    Row(
        modifier = modifier
            .alpha(alpha)
            .clearAndSetSemantics { contentDescription = description }
            .shadow(elevation = 6.dp, shape = shape, clip = false)
            .clip(shape)
            .background(Background)
            .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BrainView(state = brainState, modifier = Modifier.size(32.dp), contentDescription = "")
        NText(count.toString(), 20.sp, color = TextColor, weight = FontWeight.Black, maxLines = 1)
    }
}

private val Background = Color(0xFFFBE7B6)
private val TextColor = Color(0xFF4A2A12)

private const val IDLE_ALPHA = 0.22f
private const val FADE_DURATION_MS = 500
private const val IDLE_TIMEOUT_MS = 2_500L

@Preview(name = "Floating counter, active", widthDp = 200)
@Composable
private fun FloatingCounterActivePreview() {
    DuelTheme { FloatingCounter(count = 14, limit = 100) }
}

@Preview(name = "Floating counter, target reached", widthDp = 200)
@Composable
private fun FloatingCounterTargetPreview() {
    DuelTheme { FloatingCounter(count = 100, limit = 100) }
}
