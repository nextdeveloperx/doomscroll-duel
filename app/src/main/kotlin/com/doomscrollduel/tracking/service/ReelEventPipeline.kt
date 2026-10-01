package com.doomscrollduel.tracking.service

import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.doomscrollduel.tracking.detector.ReelEventProcessor
import com.doomscrollduel.tracking.detector.ReelSignal
import com.doomscrollduel.tracking.model.TrackedApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Everything the accessibility service does with an event except being a service: consent check, turning the event into a
 * [ReelSignal], counting, and saving. It exists so the same code can be driven by real [AccessibilityEvent] objects in an
 * instrumented test (`ReelCounterInstrumentedTest`) without a phone's Accessibility settings.
 *
 * PRIVACY CONTRACT (same as the service): from an event it reads only the package name, event type, event time and, for a
 * scroll, the view's class name and layout resource id. Never text, descriptions or anything else.
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
    /** Handles one event. Returns the tracked app it came from (so the blocker can look), or null when it was ignored. */
    fun onEvent(event: AccessibilityEvent?): TrackedApp? {
        if (!consent()) return null
        val e = event ?: return null
        val app = TrackedApp.fromPackage(e.packageName?.toString()) ?: return null
        val signal = when (e.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> scrollSignal(app, e)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> ReelSignal.ContentChanged(app, e.eventTime)
            // A window change carries no data we use; it only tells the blocker that the app came to the front.
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> null
            else -> return null
        }
        if (signal != null && processor.process(signal)) {
            val countedAt = wallClock()
            scope.launch { onCounted(app, record(app, countedAt)) }
        }
        return app
    }

    /** Reads only the scrolled view's class name and resource id. Never its text. */
    private fun scrollSignal(app: TrackedApp, e: AccessibilityEvent): ReelSignal.Scroll {
        val className = e.className?.toString()
        val viewId = viewIdOf(e)
        if (debugLog) Log.d(DIAG_TAG, "scroll pkg=${app.packageName} class=$className viewId=$viewId")
        return ReelSignal.Scroll(app, e.eventTime, className, viewId)
    }

    private companion object {
        /** Debug builds only: lets testers find the right ids for `assets/surface_rules.json`. Nothing is stored. */
        const val DIAG_TAG = "ReelSurfaceDiag"
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
