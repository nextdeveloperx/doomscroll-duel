package com.doomscrollduel.blocking

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import com.doomscrollduel.feature.blocking.BlockingOverlay
import com.doomscrollduel.feature.blocking.OverlayActions
import com.doomscrollduel.feature.blocking.OverlayUi
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Shows the full-screen "Brain bachao" and Wait-10 cards. It uses an accessibility overlay window
 * (`TYPE_ACCESSIBILITY_OVERLAY`), which an accessibility service may add without the "draw over other apps"
 * permission. The window covers the whole screen and takes the touches, so the reel underneath cannot be used.
 */
class OverlayController(
    private val service: AccessibilityService,
    private val actions: OverlayActions,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val _ui = MutableStateFlow<OverlayUi>(OverlayUi.Hidden)
    val ui: StateFlow<OverlayUi> = _ui

    private var view: ComposeView? = null
    private var owner: OverlayLifecycleOwner? = null

    val isShowing: Boolean get() = view != null

    /** Shows or updates the overlay. Cheap to call every second. */
    fun show(ui: OverlayUi) {
        if (ui == OverlayUi.Hidden) {
            hide()
            return
        }
        _ui.value = ui
        if (view == null) attach()
    }

    fun hide() {
        _ui.value = OverlayUi.Hidden
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
                    val current by _ui.collectAsState()
                    BlockingOverlay(current, actions)
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
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
}

/** Compose needs a lifecycle and a saved-state registry, and a service window has neither, so we provide them. */
private class OverlayLifecycleOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    fun start() {
        savedState.performAttach()
        savedState.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun stop() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
