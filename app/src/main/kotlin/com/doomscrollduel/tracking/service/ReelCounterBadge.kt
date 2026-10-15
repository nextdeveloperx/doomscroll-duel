package com.doomscrollduel.tracking.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.doomscrollduel.blocking.OverlayLifecycleOwner
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import com.doomscrollduel.domain.repository.ReelLimitStore
import com.doomscrollduel.domain.repository.ReelRepository
import com.doomscrollduel.feature.counter.FloatingCounter
import com.doomscrollduel.feature.counter.PILL_HEIGHT
import com.doomscrollduel.feature.counter.cutoutGapCenterOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The person's choice to see the on-screen counter. On by default; Settings switches it. */
object CounterBadgePrefs {
    private const val FILE = "counter_badge"
    private const val KEY = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).apply()
    }
}

/**
 * Shows today's reel count at the top of the screen while a tracked app is in use. It is an accessibility overlay
 * window (no "draw over other apps" permission) that cannot be touched, so it never blocks the app underneath.
 *
 * It appears on the first event from a tracked app and stays on screen the whole time that app is in front — it
 * never disappears on its own while the person keeps using the app. [FloatingCounter] fades it to a faint opacity
 * once no new reel has landed for a few seconds (full opacity again the moment one does), and locks it fully
 * visible once today's limit is reached, with no idle fade at all. The window itself only comes down when the
 * tracked app is no longer in front (checked a few times a second, since leaving it raises no event of its own) or
 * the person has turned the on-screen counter off in Settings.
 *
 * Dynamic-Island-style placement, measured on-device rather than guessed:
 *
 * Vertically ([topOffsetPx]): on a device with a real camera cutout, there is nowhere *below* the status bar that
 * is safe from a tracked app's own header — Instagram's Reels screen, for one, draws its own centered "Reels" title
 * starting right at the status bar's bottom edge, so any pill placed there collides with it regardless of how small
 * the margin is. The one row that is genuinely empty next to the cutout is the status bar's own band: nothing else
 * occupies the horizontal space immediately beside a centered punch-hole (the clock sits far left, battery/signal
 * icons far right), so the pill is centered vertically *within* that band — overlapping the cutout's own row, never
 * the app underneath — only falling back to sitting just below the status bar (the old behaviour) on a device with
 * no cutout to hug, where that band is usually too short for a 34dp pill to fit inside at all.
 *
 * Horizontally (in [attach], using [com.doomscrollduel.feature.counter.cutoutGapCenterOffset]): the pill does not
 * just center itself on the cutout as a whole — it reserves a gap between the mascot and the count sized to the
 * cutout's own measured width (plus a little clearance), and is anchored by its *left* edge so that gap's center,
 * not the pill's own geometric center, lands exactly on the physical cutout. That keeps the mascot to the cutout's
 * left and the count to its right, with neither glyph ever rendering where the lens sits, and keeps working as the
 * count grows from "9" to "1,000" — a wider count only pushes the pill's right edge further out, it never drags
 * the gap (or the mascot) off the cutout. A phone with no cutout at all gets the plain, ungapped, screen-centered
 * layout instead ([FloatingCounter]'s `cutoutClearanceDp = null` default).
 *
 * Both halves are read fresh from the real system insets and cutout geometry on every (re)attach, each applied
 * exactly once (the window `y` here, the Compose `offset` there) — [FloatingCounter] itself carries no positioning
 * logic beyond reserving the gap it's told to reserve.
 */
class ReelCounterBadge(
    private val service: AccessibilityService,
    private val scope: CoroutineScope,
    private val repository: ReelRepository,
    private val limits: ReelLimitStore,
    private val probe: ReelScreenProbe,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private var owner: OverlayLifecycleOwner? = null
    private var watchJob: Job? = null

    /** Any event from a tracked app. Cheap once the badge is up: the fade/lock state lives inside FloatingCounter. */
    fun onTrackedEvent() {
        if (!CounterBadgePrefs.isEnabled(service)) {
            hide()
            return
        }
        if (view == null) attach()
        if (watchJob == null) {
            // Leaving the app sends us no event (only the four apps are heard), so look at who is in front a few times a second.
            watchJob = scope.launch {
                while (true) {
                    delay(WATCH_MS)
                    if (!probe.trackedAppInFront(service)) {
                        hide()
                        break
                    }
                }
            }
        }
    }

    fun hide() {
        watchJob?.cancel()
        watchJob = null
        val v = view ?: return
        runCatching { windowManager.removeView(v) }
        owner?.stop()
        view = null
        owner = null
    }

    private fun attach() {
        val lifecycleOwner = OverlayLifecycleOwner().also { it.start() }
        // Read the cutout's bounds exactly once per attach and derive everything (vertical offset, horizontal
        // gap, horizontal anchor) from this same Rect, rather than querying WindowManager repeatedly — keeps the
        // vertical and horizontal halves of this layout consistent with each other on every attach.
        val density = service.resources.displayMetrics.density
        val cutout = cutoutRectPx()
        val cutoutClearanceDp = cutout?.let { ((it.width() / density) + CUTOUT_EXTRA_CLEARANCE_DP).dp }
        val pillAlignment: Alignment
        val pillOffsetDp: Dp
        if (cutout != null && cutoutClearanceDp != null) {
            pillAlignment = Alignment.TopStart
            val gapCenterOffsetDp = cutoutGapCenterOffset(cutoutClearanceDp)
            pillOffsetDp = (cutout.centerX() / density).dp - gapCenterOffsetDp
        } else {
            pillAlignment = Alignment.TopCenter
            pillOffsetDp = 0.dp
        }
        val composeView = ComposeView(service).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                DuelTheme {
                    val count by repository.observeToday().map { it.total }.collectAsState(initial = 0)
                    val limit by limits.limit.collectAsState(initial = ReelLimitStore.DEFAULT)
                    // Full-width Box (rather than a WRAP_CONTENT window) so the pill can be anchored by its own
                    // left edge when there's a cutout to straddle, with no dependency on its measured width.
                    Box(modifier = Modifier.fillMaxWidth()) {
                        FloatingCounter(
                            count = count,
                            limit = limit,
                            cutoutClearanceDp = cutoutClearanceDp,
                            modifier = Modifier.align(pillAlignment).offset(x = pillOffsetDp),
                        )
                    }
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            y = topOffsetPx(cutout, density)
        }
        runCatching { windowManager.addView(composeView, params) }
            .onSuccess {
                view = composeView
                owner = lifecycleOwner
            }
            .onFailure { lifecycleOwner.stop() }
    }

    /**
     * How far down from the very top of the screen the badge's window should start, in pixels.
     *
     * With a real camera cutout ([cutout] non-null): the pill is centered *within* the status-bar/cutout band
     * itself — the only row that both hugs the cutout and is free of any tracked app's own header — clamped to
     * never go negative if that band is somehow shorter than the pill (a very slim cutout height). Without one:
     * falls back to sitting just below the status bar with a small fixed margin, the old behaviour, since there's
     * no cutout to hug and this device's status-bar band is usually too short to center a 34dp pill inside anyway.
     *
     * The status-bar/cutout inset itself is read fresh from the real system insets on every (re)attach (API 30+:
     * [WindowManager.getCurrentWindowMetrics]; older: the system's own status-bar height resource, the same value
     * `statusBarsPadding()` would have used) rather than a screen-specific guess, so this lands correctly on phones
     * with different status-bar heights and cutout shapes.
     */
    private fun topOffsetPx(cutout: Rect?, density: Float): Int {
        val inset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bars = windowManager.currentWindowMetrics.windowInsets
                .getInsets(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout())
            bars.top
        } else {
            @Suppress("DiscouragedApi")
            val id = service.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (id > 0) service.resources.getDimensionPixelSize(id) else (DEFAULT_STATUS_BAR_DP * density).toInt()
        }
        val pillHeightPx = PILL_HEIGHT.value * density
        return if (cutout != null) {
            ((inset - pillHeightPx) / 2f).toInt().coerceAtLeast(0)
        } else {
            inset + (EXTRA_MARGIN_DP * density).toInt()
        }
    }

    /** The physical camera cutout's bounds in pixels, or null on a device with no cutout (or pre-API 28). */
    private fun cutoutRectPx(): Rect? {
        val cutout = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> windowManager.currentWindowMetrics.windowInsets.displayCutout
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> legacyDisplayCutout()
            else -> null
        }
        val rect: Rect = cutout?.boundingRectTop ?: return null
        return if (rect.isEmpty) null else rect
    }

    /** [android.view.Display.getCutout] is deprecated in favour of [WindowManager.getCurrentWindowMetrics] (API 30+); this is only reached below that. */
    @Suppress("DEPRECATION")
    private fun legacyDisplayCutout() = windowManager.defaultDisplay.cutout

    private companion object {
        const val WATCH_MS = 500L

        /** Breathing room below the status bar / cutout on a device with NO cutout — never sits flush against it. */
        const val EXTRA_MARGIN_DP = 6f

        /** Pre-API 30 fallback only, for the rare device where the status-bar dimen resource is missing. */
        const val DEFAULT_STATUS_BAR_DP = 24f

        /** Extra width either side of the cutout's own measured width, so neither glyph sits flush against the lens. */
        const val CUTOUT_EXTRA_CLEARANCE_DP = 8f
    }
}
