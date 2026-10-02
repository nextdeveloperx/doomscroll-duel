package com.doomscrollduel.tracking.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
 * It appears on the first event from a tracked app and goes away after [IDLE_HIDE_MS] without one.
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
    private var hideJob: Job? = null
    private var watchJob: Job? = null

    /** Any event from a tracked app. Cheap: only re-arms the hide timer once the badge is up. */
    fun onTrackedEvent() {
        if (!CounterBadgePrefs.isEnabled(service)) {
            hide()
            return
        }
        if (view == null) attach()
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(IDLE_HIDE_MS)
            hide()
        }
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
        hideJob?.cancel()
        hideJob = null
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
                    FloatingCounter(count = count, limit = limit)
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
        }
        runCatching { windowManager.addView(composeView, params) }
            .onSuccess {
                view = composeView
                owner = lifecycleOwner
            }
            .onFailure { lifecycleOwner.stop() }
    }

    private companion object {
        const val IDLE_HIDE_MS = 12_000L
        const val WATCH_MS = 500L
    }
}
