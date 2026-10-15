package com.doomscrollduel.tracking.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
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
        val composeView = ComposeView(service).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                DuelTheme {
                    val count by repository.observeToday().map { it.total }.collectAsState(initial = 0)
                    val limit by limits.limit.collectAsState(initial = ReelLimitStore.DEFAULT)
                    // statusBarsPadding keeps it clear of the status bar and any display cutout; the window itself
                    // sits at the very top of the screen so this is the only thing pushing it down from there.
                    FloatingCounter(count = count, limit = limit, modifier = Modifier.statusBarsPadding())
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (8 * service.resources.displayMetrics.density).toInt()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        runCatching { windowManager.addView(composeView, params) }
            .onSuccess {
                view = composeView
                owner = lifecycleOwner
            }
            .onFailure { lifecycleOwner.stop() }
    }

    private companion object {
        const val WATCH_MS = 500L
    }
}
