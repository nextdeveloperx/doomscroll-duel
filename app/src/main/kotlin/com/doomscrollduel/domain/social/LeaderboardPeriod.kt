package com.doomscrollduel.domain.social

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.IsoFields

/**
 * Pure period-key and scoring math behind the two leaderboards (see [LeaderboardRepository]): Dost (friends)
 * resets every ISO week, India (everyone) resets every calendar month. Pulled out of
 * [com.doomscrollduel.data.social.FirebaseLeaderboardRepository] so the period-boundary and "a score from an
 * earlier period reads as zero" rules can be unit-tested without a live Firestore. No behaviour change: the key
 * formats here are byte-for-byte what was already being written to and read from the deployed `directory/{uid}`
 * documents — changing the format would make every already-stored period key look stale and silently zero out
 * every real person's score on their next read, so these must never be "tidied up" (e.g. zero-padding the week
 * number) without a one-time migration of the live data.
 */
object LeaderboardPeriod {

    /** "2026-W42" — the ISO week-based year and week number, which can differ from the plain calendar year for the
     * first/last few days of January and December (by design: that is what ISO 8601 weeks mean). */
    fun weekKey(date: LocalDate): String = "${date.get(IsoFields.WEEK_BASED_YEAR)}-W${date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"

    /** "2026-01" — the calendar year and month. */
    fun monthKey(date: LocalDate): String = date.toString().substring(0, 7)

    /**
     * What a leaderboard row should show for a stored (coins, periodKey) pair, given the period being displayed
     * right now: the stored total only counts while its period key still matches; a score written in an earlier
     * week or month has rolled over and reads as zero, never negative, never carried forward.
     */
    fun scoreFor(storedCoins: Long, storedPeriodKey: String?, currentPeriodKey: String): Int =
        if (storedPeriodKey == currentPeriodKey) storedCoins.coerceAtLeast(0L).toInt() else 0

    /** Sums only the ops at or after [since] — the start of the week or month being scored. */
    fun sumSince(ops: List<Pair<Int, Instant>>, since: Instant): Int = ops.filter { it.second >= since }.sumOf { it.first }

    /** The signed-in person's 1-based rank and entry within [entries], or null when they are genuinely not present
     * — callers must show a loading/unavailable state instead of inventing a rank. */
    fun rankOf(entries: List<LeaderboardEntry>): Pair<Int, LeaderboardEntry>? {
        val index = entries.indexOfFirst { it.isMe }
        return if (index >= 0) (index + 1) to entries[index] else null
    }
}
