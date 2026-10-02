package com.doomscrollduel.tracking.detector

import com.doomscrollduel.tracking.model.TrackedApp
import kotlin.math.abs

/**
 * Turns accessibility signals into "one reel was swiped to". Pure Kotlin, no Android types.
 *
 * Two ways of recognising a reel swipe, chosen per app by its [SurfaceRule]:
 *
 * **By surface** (Instagram, YouTube, and Facebook's full-screen reel viewer). A reel is counted when a content change follows
 * a scroll of the short-video pager:
 *  1. A scroll on a matching surface starts a swipe. Scroll events that keep coming within
 *     [BURST_GAP_MILLIS] of each other are the same swipe and are not separate swipes.
 *  2. The next window-content change within [SWIPE_WINDOW_MILLIS] is the new video appearing: count it.
 *  3. Two counted reels are never closer than [MIN_GAP_MILLIS]; a swipe that would be is ignored.
 *
 * **By distance** (Facebook's Reels tab, whose view ids are hidden, so it looks exactly like the feed). A reels pager
 * snaps one whole page per swipe, so however short or long the finger moved, the scroll of one swipe adds up to the same
 * distance (the height of the page). The feed scrolls by whatever the finger did. A swipe is a reel when its total is within
 * [PAGE_TOLERANCE] of a total seen just before (or of the page-height hint, if one is set). It is decided when the swipe has stopped moving,
 * see [flush].
 *
 * **Direction.** Only a swipe to the NEXT reel counts. Going back to the one before does not (finger moving down, scroll
 * distance negative). When the phone does not report a direction the swipe counts as before.
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

        /** This swipe is going back to the previous reel; set from the first scroll that reports a direction. */
        var backward = false
        var directionKnown = false

        // by distance
        var burstOpen = false
        var burstTotal = 0L
        var burstLast = NEVER
        val recent = ArrayDeque<Long>()
    }

    private val states = Array(TrackedApp.entries.size) { AppState() }

    /**
     * About one page of the Facebook Reels tab in pixels (a little under the screen height), so the very first swipe of a
     * session can already be recognised. 0 = no hint; the first swipe then only teaches the size.
     */
    @Volatile var pageHintPx: Int = 0

    /** True for apps that are recognised by distance, which need [flush] called shortly after the last scroll. */
    fun decidesByDistance(app: TrackedApp): Boolean = rules().isPagerByDistance(app)

    /** Returns true when this signal completed a swipe, i.e. one more reel was watched. */
    @Synchronized
    fun process(signal: ReelSignal): Boolean {
        val state = states[signal.app.ordinal]
        val at = signal.atMillis
        return when (signal) {
            is ReelSignal.Scroll -> {
                val r = rules()
                if (r.matchesScroll(signal.app, signal.className, signal.viewId, signal.windowClass)) {
                    surfaceScroll(state, signal)
                    false
                } else if (r.isPagerByDistance(signal.app)) {
                    distanceScroll(state, signal)
                } else {
                    false
                }
            }
            is ReelSignal.ContentChanged -> {
                if (!state.swipeArmed) return false
                if (at - state.lastScrollAt > swipeWindowMillis) {
                    state.swipeArmed = false
                    return false
                }
                state.swipeArmed = false
                // A swipe back to the previous reel is not a new reel.
                if (state.backward) return false
                // Too soon after the previous reel: this swipe is ignored (and so is anything it still emits).
                if (at - state.lastCountedAt < minGapMillis) return false
                state.lastCountedAt = at
                true
            }
        }
    }

    private fun surfaceScroll(state: AppState, signal: ReelSignal.Scroll) {
        val at = signal.atMillis
        val sameBurst = at - state.lastScrollAt < burstGapMillis
        state.lastScrollAt = at
        if (!sameBurst) {
            state.backward = false
            state.directionKnown = false
        }
        // The first scroll of a swipe that says which way it goes decides the direction of the whole swipe.
        if (!state.directionKnown && signal.deltaY != 0) {
            state.backward = signal.deltaY < 0
            state.directionKnown = true
        }
        // A burst that already produced its count (or was rejected) must not start another swipe.
        if (!sameBurst || state.swipeArmed) state.swipeArmed = true
    }

    /** Adds this scroll to the swipe in progress. A pause since the previous scroll closes that swipe first. */
    private fun distanceScroll(state: AppState, signal: ReelSignal.Scroll): Boolean {
        if (signal.deltaY == 0) return false // sideways scrolls (story strips) and events without a distance say nothing
        val at = signal.atMillis
        var counted = false
        if (state.burstOpen && at - state.burstLast >= burstGapMillis) counted = finishBurst(state, state.burstLast + burstGapMillis)
        if (!state.burstOpen) {
            state.burstOpen = true
            state.burstTotal = 0
        }
        state.burstTotal += signal.deltaY
        state.burstLast = at
        return counted
    }

    /**
     * Call a little after the last scroll of an app that is recognised by distance: [nowMillis] is the event clock. Returns true
     * when the swipe that just stopped was a forward swipe to the next reel.
     */
    @Synchronized
    fun flush(app: TrackedApp, nowMillis: Long): Boolean {
        val state = states[app.ordinal]
        if (!state.burstOpen || nowMillis - state.burstLast < burstGapMillis) return false
        return finishBurst(state, nowMillis)
    }

    private fun finishBurst(state: AppState, at: Long): Boolean {
        state.burstOpen = false
        val total = state.burstTotal
        state.burstTotal = 0
        val size = abs(total)
        if (size < MIN_PAGE_PX) return false
        val references = state.recent.toList() + pageHintPx.toLong()
        val isPage = references.any { it > 0 && abs(size - it) <= it * PAGE_TOLERANCE }
        state.recent.addLast(size)
        while (state.recent.size > RECENT) state.recent.removeFirst()
        if (!isPage || total < 0) return false
        if (at - state.lastCountedAt < minGapMillis) return false
        state.lastCountedAt = at
        return true
    }

    /** Forget pending swipes, e.g. when the service reconnects. Counted history is not touched. */
    @Synchronized
    fun reset() {
        states.forEach {
            it.lastScrollAt = NEVER
            it.swipeArmed = false
            it.backward = false
            it.directionKnown = false
            it.burstOpen = false
            it.burstTotal = 0
            it.recent.clear()
        }
    }

    companion object {
        const val MIN_GAP_MILLIS = 600L
        const val SWIPE_WINDOW_MILLIS = 1_500L
        const val BURST_GAP_MILLIS = 300L

        /** Totals this close to a known page height (1.5 percent) count as one page. Measured: pager swipes differ by under 0.1 percent. */
        const val PAGE_TOLERANCE = 0.015

        /** A scroll total below this is a nudge, not a page. */
        const val MIN_PAGE_PX = 300L
        private const val RECENT = 6
        private const val NEVER = -1_000_000_000_000L
    }
}
