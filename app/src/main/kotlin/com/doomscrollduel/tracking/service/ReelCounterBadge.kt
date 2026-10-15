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
 * Dynamic-Island-style placement: the pill sits snug against the bottom of the status bar / camera cutout
 * ([topOffsetPx], the window's own `y`) and is horizontally centered on the physical cutout itself, not just the
 * middle of the screen ([cutoutCenterOffsetDp], a Compose offset on top of a full-width, centered Box) — so an
 * off-center punch-hole (left-aligned, say) still gets the pill hugging it rather than sitting at screen-center and
 * missing it. Both numbers are read fresh from the real system insets and cutout geometry every time the window is
 * (re)attached, never a fixed dp guess, and a phone with no cutout at all just falls back to true screen-center.
 * Each offset is applied exactly once — the window `y` here, the Compose `offset` there — so there is nothing to
 * double up; [FloatingCounter] itself carries no positioning logic of its own.
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
        val cutoutOffset = cutoutCenterOffsetDp()
        val composeView = ComposeView(service).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                DuelTheme {
                    val count by repository.observeToday().map { it.total }.collectAsState(initial = 0)
                    val limit by limits.limit.collectAsState(initial = ReelLimitStore.DEFAULT)
                    // Full-width Box + TopCenter + offset (rather than a WRAP_CONTENT window centered on the
                    // screen) so the pill lands centered on the physical cutout even when that cutout is not
                    // itself in the middle of the screen, with no dependency on the pill's own measured width.
                    Box(modifier = Modifier.fillMaxWidth()) {
                        FloatingCounter(
                            count = count,
                            limit = limit,
                            modifier = Modifier.align(Alignment.TopCenter).offset(x = cutoutOffset),
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
            y = topOffsetPx()
        }
        runCatching { windowManager.addView(composeView, params) }
            .onSuccess {
                view = composeView
                owner = lifecycleOwner
            }
            .onFailure { lifecycleOwner.stop() }
    }

    /**
     * How far down from the very top of the screen the badge's window should start, in pixels: tall enough to
     * clear whichever is taller on this phone, the status bar or the camera cutout, plus a small fixed margin so
     * it never sits flush against either. Read fresh from the real system insets every time the window is
     * (re)attached (API 30+: [WindowManager.getCurrentWindowMetrics]; older: the system's own status-bar height
     * resource, the same value `statusBarsPadding()` would have used) rather than a screen-specific guess, so it
     * lands in the same place on phones with different status-bar heights and cutout shapes. The margin is kept
     * deliberately small — a Dynamic Island hugs its cutout, it doesn't float well below it.
     */
    private fun topOffsetPx(): Int {
        val density = service.resources.displayMetrics.density
        val inset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bars = windowManager.currentWindowMetrics.windowInsets
                .getInsets(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout())
            bars.top
        } else {
            @Suppress("DiscouragedApi")
            val id = service.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (id > 0) service.resources.getDimensionPixelSize(id) else (DEFAULT_STATUS_BAR_DP * density).toInt()
        }
        return inset + (EXTRA_MARGIN_DP * density).toInt()
    }

    /**
     * How far the pill's own center should sit from the screen's horizontal center, so that it lands on the
     * physical camera cutout's center instead — zero (true screen-center) on a device with no cutout, or if the
     * cutout's bounds could not be read. [cutoutCenterXPx] is the only thing here that can vary by device; this
     * just converts that to a density-independent offset.
     */
    private fun cutoutCenterOffsetDp(): Dp {
        val cutoutCenterX = cutoutCenterXPx() ?: return 0.dp
        val density = service.resources.displayMetrics.density
        val screenWidthPx = service.resources.displayMetrics.widthPixels
        return ((cutoutCenterX - screenWidthPx / 2) / density).dp
    }

    /** The physical camera cutout's horizontal center in pixels, or null on a device with no cutout (or pre-API 28). */
    private fun cutoutCenterXPx(): Int? {
        val cutout = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> windowManager.currentWindowMetrics.windowInsets.displayCutout
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> legacyDisplayCutout()
            else -> null
        }
        val rect: Rect = cutout?.boundingRectTop ?: return null
        return if (rect.isEmpty) null else rect.centerX()
    }

    /** [android.view.Display.getCutout] is deprecated in favour of [WindowManager.getCurrentWindowMetrics] (API 30+); this is only reached below that. */
    @Suppress("DEPRECATION")
    private fun legacyDisplayCutout() = windowManager.defaultDisplay.cutout

    private companion object {
        const val WATCH_MS = 500L

        /** Breathing room below the status bar / cutout — just enough that the pill never sits flush against it. */
        const val EXTRA_MARGIN_DP = 6f

        /** Pre-API 30 fallback only, for the rare device where the status-bar dimen resource is missing. */
        const val DEFAULT_STATUS_BAR_DP = 24f
    }
}
