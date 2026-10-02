package com.doomscrollduel.core.designsystem.brain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * True once a 3D brain page has loaded and drawn its first frames. The opening splash waits for this (up to a limit), so the
 * brain is already standing on Home when the splash lifts. Process-wide: the first brain on any screen is enough.
 */
object Brain3DReady {
    private val ready = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = ready

    fun markReady() {
        ready.value = true
    }
}
