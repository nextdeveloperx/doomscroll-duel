package com.doomscrollduel.tracking.detector

import com.doomscrollduel.tracking.model.TrackedApp

/**
 * The only input the counter ever sees: which app, which kind of event, when.
 * A scroll also carries the scrolled view's class name and layout resource id (for example
 * "reel_recycler") and the class name of the app's front screen (for example a full-screen reel viewer activity),
 * which are layout identifiers, never anything the user sees or watches.
 * No text, description, username or caption can be put in here.
 */
sealed interface ReelSignal {
    val app: TrackedApp

    /** Event time in milliseconds on the uptime clock (`AccessibilityEvent.getEventTime`). */
    val atMillis: Long

    class Scroll(
        override val app: TrackedApp,
        override val atMillis: Long,
        val className: String?,
        val viewId: String?,
        /** Class name of the app's front screen (an activity such as a full-screen reel viewer), or null when not known. */
        val windowClass: String? = null,
        /** How far the content moved up (+) or down (-) in pixels, from the phone; 0 when it did not say. A number only, no content. */
        val deltaY: Int = 0,
    ) : ReelSignal {
        // Never print the identifiers, even by accident in a log line.
        override fun toString() = "Scroll(app=$app, atMillis=$atMillis)"
    }

    class ContentChanged(
        override val app: TrackedApp,
        override val atMillis: Long,
    ) : ReelSignal {
        override fun toString() = "ContentChanged(app=$app, atMillis=$atMillis)"
    }
}
