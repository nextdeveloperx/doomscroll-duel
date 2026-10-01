package com.doomscrollduel.domain.blocking

/**
 * WAIT-10 GATE. When a reel screen opens, a 10 second countdown card covers it. "Rehne do" leaves at any time.
 * "Dekhna hai" only works after the 10 seconds are over. After that the user watches freely until they leave
 * the reel screen (the session ends), and the next time they open it the gate comes back.
 *
 * Times are `elapsedRealtime` (monotonic), so changing the phone clock cannot skip the countdown.
 */
object WaitGate {
    const val COUNTDOWN_MS = 10_000L

    /** Leaving the reel screen for longer than this ends the session, so the next open shows the gate again. */
    const val LEAVE_GRACE_MS = 3_000L

    /** No sign of the app for this long and then it comes back to the front: a new session. */
    const val IDLE_NEW_SESSION_MS = 20_000L
}

sealed interface GateState {
    /** Counting down. [remainingMs] is 10 000 down to 1. */
    data class Counting(val remainingMs: Long) : GateState

    /** The 10 seconds are over; "Dekhna hai" is enabled. */
    data object Ready : GateState

    /** The user chose to continue. No gate until the session ends. */
    data object Passed : GateState
}

/** One visit to a reel screen. */
data class GateSession(
    val openedAtElapsedMs: Long,
    val continuedAtElapsedMs: Long? = null,
    /** When the reel screen was last seen open, to decide when the user has really left. */
    val lastSeenElapsedMs: Long = openedAtElapsedMs,
) {
    fun state(nowElapsedMs: Long): GateState {
        if (continuedAtElapsedMs != null) return GateState.Passed
        val left = WaitGate.COUNTDOWN_MS - (nowElapsedMs - openedAtElapsedMs)
        return if (left > 0) GateState.Counting(left) else GateState.Ready
    }

    /** "Dekhna hai" is only accepted once the countdown is over. Returns the new session, or null if too early. */
    fun continued(nowElapsedMs: Long): GateSession? =
        // Pressing the button means the user is on the reel screen right now.
        if (state(nowElapsedMs) == GateState.Ready) copy(continuedAtElapsedMs = nowElapsedMs, lastSeenElapsedMs = nowElapsedMs) else null

    fun seen(nowElapsedMs: Long): GateSession = copy(lastSeenElapsedMs = nowElapsedMs)

    /** The session is over when the screen has not been seen for the grace period. */
    fun hasEnded(nowElapsedMs: Long): Boolean = nowElapsedMs - lastSeenElapsedMs > WaitGate.LEAVE_GRACE_MS
}
