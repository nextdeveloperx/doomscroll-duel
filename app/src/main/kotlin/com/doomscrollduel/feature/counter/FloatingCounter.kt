package com.doomscrollduel.feature.counter

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.Dp
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
 * The Dynamic-Island-style pill drawn over Instagram, YouTube, Facebook and Snapchat: today's reels, with our own
 * mascot brain on the left and the number on the right. Slim by design (a 34dp-tall pill, not the bulkier badge
 * this used to be) so it reads as a native system overlay straddling the camera cutout rather than a separate
 * floating card. Shows only our own number (never anything from the other app) and takes no touches.
 * [defaultMinSize] gives it a sensible lower bound for a one- or two-digit count without ever clipping a wider one
 * (610, 1,000, ...) — the Row simply grows further right.
 *
 * [cutoutClearanceDp], when the device has a real camera cutout, reserves that much empty width between the mascot
 * and the number — sized by [com.doomscrollduel.tracking.service.ReelCounterBadge] to the cutout's own measured
 * width, plus a little breathing room — so neither glyph ever renders where the physical lens sits, regardless of
 * how many digits the count grows to. [com.doomscrollduel.tracking.service.ReelCounterBadge.cutoutGapCenterOffset]
 * is the matching half of this: it tells the caller exactly how far this gap's own center sits from the pill's
 * left edge, so the pill can be anchored by that edge (not centered as a whole) with the gap's center landing
 * precisely on the cutout — a growing count only ever pushes the pill's right edge further out, never the gap.
 * `null` (the default, and what every other tracked-app surface without this exact layout need gets) renders the
 * old compact layout with no reserved gap at all.
 *
 * Visual states (see the class doc on [com.doomscrollduel.tracking.service.ReelCounterBadge] for the window
 * lifecycle, and for how this gets positioned relative to the physical cutout): full opacity right after a new
 * reel is counted, fading to a faint but still-present [IDLE_ALPHA] once nothing new has landed for
 * [idleTimeoutMillis], and locked back to full opacity for as long as [limit] has been reached or passed — the idle
 * timer never fades a target-reached badge. Reaching zero reels again (a new day) naturally drops out of that state
 * since `count >= limit` stops being true.
 */
@Composable
fun FloatingCounter(
    count: Int,
    limit: Int,
    modifier: Modifier = Modifier,
    cutoutClearanceDp: Dp? = null,
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
            .defaultMinSize(minWidth = PILL_MIN_WIDTH, minHeight = PILL_HEIGHT)
            .alpha(alpha)
            .clearAndSetSemantics { contentDescription = description }
            .shadow(elevation = 3.dp, shape = shape, clip = false)
            .clip(shape)
            .background(Background)
            .padding(start = ROW_START_PADDING, end = ROW_END_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ROW_SPACING),
    ) {
        BrainView(state = brainState, modifier = Modifier.size(ICON_SIZE), contentDescription = "")
        if (cutoutClearanceDp != null) {
            Spacer(modifier = Modifier.width(cutoutClearanceDp))
        }
        NText(count.toString(), 16.sp, color = TextColor, weight = FontWeight.SemiBold, maxLines = 1)
    }
}

/**
 * Where [cutoutClearanceDp]'s own center sits, measured from this pill's left edge — fixed regardless of the count
 * text's width, since the gap always comes right after the mascot. [com.doomscrollduel.tracking.service.ReelCounterBadge]
 * uses this to anchor the pill by its left edge rather than its center, so the gap (not the pill as a whole) lands
 * on the physical cutout and a growing count never drags that alignment off target.
 */
fun cutoutGapCenterOffset(cutoutClearanceDp: Dp): Dp =
    ROW_START_PADDING + ICON_SIZE + ROW_SPACING + cutoutClearanceDp / 2

private val Background = Color(0xFFFBE7B6)
private val TextColor = Color(0xFF4A2A12)

private const val IDLE_ALPHA = 0.22f
private const val FADE_DURATION_MS = 500
private const val IDLE_TIMEOUT_MS = 2_500L

/**
 * Dynamic-Island-style targets: slim (34dp tall), roomy enough for a short count without padding a long one.
 * [PILL_HEIGHT] is not private — [com.doomscrollduel.tracking.service.ReelCounterBadge] needs the real value to
 * center the pill within the status-bar/cutout band rather than guessing at its height from the outside.
 */
val PILL_HEIGHT = 34.dp
private val PILL_MIN_WIDTH = 118.dp
private val ROW_START_PADDING = 8.dp
private val ROW_END_PADDING = 12.dp
private val ROW_SPACING = 6.dp
private val ICON_SIZE = 24.dp

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

@Preview(name = "Floating counter, wide count", widthDp = 200)
@Composable
private fun FloatingCounterWideCountPreview() {
    DuelTheme { FloatingCounter(count = 1000, limit = 1500) }
}

@Preview(name = "Floating counter, cutout clearance", widthDp = 200)
@Composable
private fun FloatingCounterCutoutPreview() {
    DuelTheme { FloatingCounter(count = 348, limit = 1500, cutoutClearanceDp = 42.dp) }
}
