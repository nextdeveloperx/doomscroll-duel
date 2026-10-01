package com.doomscrollduel.tracking.detector

import com.doomscrollduel.tracking.model.TrackedApp

/**
 * The only input the counter ever sees: which app, which kind of event, when.
 * A scroll also carries the scrolled view's class name and layout resource id (for example
 * "reel_recycler"), which are layout identifiers, never anything the user sees or watches.
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
