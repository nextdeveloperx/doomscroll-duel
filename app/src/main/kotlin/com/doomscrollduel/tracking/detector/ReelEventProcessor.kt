package com.doomscrollduel.tracking.detector

import com.doomscrollduel.tracking.model.TrackedApp

/**
 * Turns accessibility signals into "one reel was swiped to". Pure Kotlin, no Android types.
 *
 * A reel is counted when a content change follows a scroll of the short-video pager:
 *  1. A scroll on a matching surface starts a swipe. Scroll events that keep coming within
 *     [BURST_GAP_MILLIS] of each other are the same swipe and are not separate swipes.
 *  2. The next window-content change within [SWIPE_WINDOW_MILLIS] is the new video appearing: count it.
 *  3. Two counted reels are never closer than [MIN_GAP_MILLIS]; a swipe that would be is ignored.
 *
 * One swipe therefore counts once, however many events it produces, and content changes with no
 * scroll before them (likes, timers, comments) never count.
 */
class ReelEventProcessor(
    private val rules: () -> SurfaceRules,
    private val minGapMillis: Long = MIN_GAP_MILLIS,
    private val swipeWindowMillis: Long = SWIPE_WINDOW_MILLIS,
    private val burstGapMillis: Long = BURST_GAP_MILLIS,
) {
    private class AppState {
        var lastCountedAt = NEVER
        var lastScrollAt = NEVER
        var swipeArmed = false
    }

    private val states = Array(TrackedApp.entries.size) { AppState() }

    /** Returns true when this signal completed a swipe, i.e. one more reel was watched. */
    @Synchronized
    fun process(signal: ReelSignal): Boolean {
        val state = states[signal.app.ordinal]
        val at = signal.atMillis
        return when (signal) {
            is ReelSignal.Scroll -> {
                if (!rules().matchesScroll(signal.app, signal.className, signal.viewId)) return false
                val sameBurst = at - state.lastScrollAt < burstGapMillis
                state.lastScrollAt = at
                // A burst that already produced its count (or was rejected) must not start another swipe.
                if (!sameBurst || state.swipeArmed) state.swipeArmed = true
                false
            }
            is ReelSignal.ContentChanged -> {
                if (!state.swipeArmed) return false
                if (at - state.lastScrollAt > swipeWindowMillis) {
                    state.swipeArmed = false
                    return false
                }
                state.swipeArmed = false
                // Too soon after the previous reel: this swipe is ignored (and so is anything it still emits).
                if (at - state.lastCountedAt < minGapMillis) return false
                state.lastCountedAt = at
                true
            }
        }
    }

    /** Forget pending swipes, e.g. when the service reconnects. Counted history is not touched. */
    @Synchronized
    fun reset() {
        states.forEach {
            it.lastScrollAt = NEVER
            it.swipeArmed = false
        }
    }

    companion object {
        const val MIN_GAP_MILLIS = 600L
        const val SWIPE_WINDOW_MILLIS = 1_500L
        const val BURST_GAP_MILLIS = 300L
        private const val NEVER = -1_000_000_000_000L
    }
}
