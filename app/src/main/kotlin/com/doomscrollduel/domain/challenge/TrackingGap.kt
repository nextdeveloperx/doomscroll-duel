package com.doomscrollduel.domain.challenge

import java.time.Duration
import java.time.Instant

/** Why reels could not be counted for a while. Only a switched-off service is the player's doing. */
enum class GapCause {
    /** The user turned the accessibility service off. This is what can forfeit a duel. */
    SERVICE_DISABLED,

    /** Phone off, no process, or unknown. Never punished, because we cannot prove it was deliberate. */
    DEVICE_OFF_OR_UNKNOWN,
}

/** A stretch of time when counting was not running. [end] is null while it is still going on. */
data class TrackingGap(val start: Instant, val end: Instant?, val cause: GapCause)

/**
 * The 10 minute rule: a player whose accessibility service stays OFF for MORE than 10 minutes in a row
 * during a challenge forfeits. Exactly 10 minutes is still fine.
 */
object ForfeitRule {
    val MAX_OFF: Duration = Duration.ofMinutes(10)

    /**
     * The instant this player crossed the limit inside `[from, to]`, or null if they never did.
     * Only [GapCause.SERVICE_DISABLED] gaps count. Gaps are clipped to the window and overlapping or
     * touching ones are merged, so splitting one long gap into pieces does not help.
     */
    fun forfeitMoment(gaps: List<TrackingGap>, from: Instant, to: Instant): Instant? {
        if (!to.isAfter(from)) return null
        val spans = gaps
            .filter { it.cause == GapCause.SERVICE_DISABLED }
            .map { maxOf(it.start, from) to minOf(it.end ?: to, to) }
            .filter { (s, e) -> e.isAfter(s) }
            .sortedBy { it.first }

        var best: Instant? = null
        var curStart: Instant? = null
        var curEnd: Instant? = null

        fun closeSpan() {
            val s = curStart ?: return
            val e = curEnd ?: return
            if (Duration.between(s, e) > MAX_OFF) {
                val moment = s.plus(MAX_OFF)
                if (best == null || moment.isBefore(best!!)) best = moment
            }
        }

        for ((s, e) in spans) {
            val end = curEnd
            if (end != null && !s.isAfter(end)) {
                if (e.isAfter(end)) curEnd = e
            } else {
                closeSpan()
                curStart = s
                curEnd = e
            }
        }
        closeSpan()
        return best
    }
}
