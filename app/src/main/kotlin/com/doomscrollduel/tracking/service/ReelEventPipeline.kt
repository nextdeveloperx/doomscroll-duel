package com.doomscrollduel.tracking.service

import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.doomscrollduel.tracking.detector.ReelEventProcessor
import com.doomscrollduel.tracking.detector.ReelSignal
import com.doomscrollduel.tracking.model.TrackedApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Everything the accessibility service does with an event except being a service: consent check, turning the event into a
 * [ReelSignal], counting, and saving. It exists so the same code can be driven by real [AccessibilityEvent] objects in an
 * instrumented test (`ReelCounterInstrumentedTest`) without a phone's Accessibility settings.
 *
 * PRIVACY CONTRACT (same as the service): from an event it reads only the package name, event type, event time and, for a
 * scroll, the view's class name, layout resource id and how far it moved (a signed number); for a window change, the class name of the screen that came to the
 * front (an activity name such as a full-screen reel viewer). Never text, descriptions or anything else.
 *
 * @param consent true only after the person agreed to the disclosure; until then every event is dropped unseen.
 * @param viewIdOf how the layout id of the scrolled view is read. Production uses [readViewId]; a test can supply its own.
 * @param record saves one counted reel and returns today's total.
 * @param onCounted called after saving, with the new total (the blocker uses it to start a lock at the limit).
 */
class ReelEventPipeline(
    private val processor: ReelEventProcessor,
    private val consent: () -> Boolean,
    private val viewIdOf: (AccessibilityEvent) -> String? = ::readViewId,
    private val record: suspend (TrackedApp, Long) -> Int,
    private val onCounted: (TrackedApp, Int) -> Unit,
    private val scope: CoroutineScope,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val debugLog: Boolean = false,
) {
    /** The front screen (activity class) last seen for each tracked app. Only the class name of the screen, never anything on it. */
    private val frontScreen = arrayOfNulls<String>(TrackedApp.entries.size)

    /** Handles one event. Returns the tracked app it came from (so the blocker can look), or null when it was ignored. */
    fun onEvent(event: AccessibilityEvent?): TrackedApp? {
        if (!consent()) return null
        val e = event ?: return null
        val app = TrackedApp.fromPackage(e.packageName?.toString()) ?: return null
        val signal = when (e.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> scrollSignal(app, e)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> ReelSignal.ContentChanged(app, e.eventTime)
            // A window change tells the blocker that the app came to the front, and which screen of it is now in front. Small
            // pop-ups and system widgets (android.* / androidx.*) do not count as a new screen.
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val screen = e.className?.toString()
                if (screen != null && !screen.startsWith("android.") && !screen.startsWith("androidx.")) frontScreen[app.ordinal] = screen
                null
            }
            else -> return null
        }
        if (signal != null && processor.process(signal)) {
            val countedAt = wallClock()
            if (debugLog) Log.d(DIAG_TAG, "COUNTED ${app.name}")
            scope.launch { onCounted(app, record(app, countedAt)) }
        }
        // An app recognised by distance is decided once its swipe has stopped moving: look again shortly after the last scroll.
        if (signal is ReelSignal.Scroll && processor.decidesByDistance(app)) {
            val lastScrollAt = signal.atMillis
            scope.launch {
                delay(FLUSH_MS)
                if (processor.flush(app, lastScrollAt + FLUSH_MS)) {
                    val countedAt = wallClock()
                    if (debugLog) Log.d(DIAG_TAG, "COUNTED ${app.name} (by distance)")
                    onCounted(app, record(app, countedAt))
                }
            }
        }
        return app
    }

    /** Reads only the scrolled view's class name and resource id. Never its text. */
    private fun scrollSignal(app: TrackedApp, e: AccessibilityEvent): ReelSignal.Scroll {
        val className = e.className?.toString()
        val viewId = viewIdOf(e)
        if (debugLog) Log.d(DIAG_TAG, "scroll pkg=${app.packageName} class=$className viewId=$viewId")
        // How far the content moved (API 28+): a number with a sign, nothing about what is on screen.
        val dy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) e.scrollDeltaY else 0
        if (debugLog) Log.d(DIAG_TAG, "scroll dy=$dy screen=${frontScreen[app.ordinal]?.substringAfterLast('.')}")
        return ReelSignal.Scroll(app, e.eventTime, className, viewId, frontScreen[app.ordinal], dy)
    }

    private companion object {
        /** Debug builds only: lets testers find the right ids for `assets/surface_rules.json`. Nothing is stored. */
        const val DIAG_TAG = "ReelSurfaceDiag"

        /** A little longer than the processor's pause between two swipes, so a swipe that has stopped is closed. */
        const val FLUSH_MS = 340L
    }
}

/** The layout resource id of the event's source view, e.g. "com.instagram.android:id/clips_viewer_view_pager". */
fun readViewId(event: AccessibilityEvent): String? {
    val source = event.source
    return try {
        source?.viewIdResourceName
    } finally {
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) source?.recycle()
    }
}
