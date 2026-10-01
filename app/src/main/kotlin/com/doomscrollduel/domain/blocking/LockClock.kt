package com.doomscrollduel.domain.blocking

/**
 * One reading of every clock we have, taken at the same moment.
 *
 * - [wallMs]: the phone's calendar clock. The user can change it, and a time-zone or network update can move it.
 * - [elapsedMs]: milliseconds since boot, including deep sleep (`SystemClock.elapsedRealtime`). It only goes
 *   forward and the user cannot change it. It restarts from zero at every reboot.
 * - [bootId]: changes at every reboot (`Settings.Global.BOOT_COUNT`), so we know when [elapsedMs] can be trusted.
 * - [trustedMs]: a time we got from our server recently and carried forward with [elapsedMs], or null when we
 *   have none. It is the only calendar time the user cannot move.
 */
data class ClockSample(
    val wallMs: Long,
    val elapsedMs: Long,
    val bootId: String?,
    val trustedMs: Long? = null,
) {
    /** The best calendar time we have: trusted if known, else the phone's own clock. */
    val calendarMs: Long get() = trustedMs ?: wallMs
}

/**
 * How much REAL time passed between two readings. Timers (lock, friend pass) add this up bit by bit
 * instead of comparing an end time with the phone's calendar, so changing the clock cannot shorten them.
 *
 *  1. Same boot: the monotonic difference. The phone clock is ignored completely: setting it forward,
 *     backward, or a time-zone change makes no difference.
 *  2. After a reboot: the monotonic clock restarted, so use the trusted time if both readings have one,
 *     otherwise the phone clock. This is the one gap: someone who sets the clock forward while the phone is
 *     off or rebooting can shorten a lock. Trusted time (from our server) closes it whenever it is available.
 *  3. A clock that moved backwards across a reboot counts as zero progress, never negative.
 */
object LockClock {
    fun advanceMs(previous: ClockSample, now: ClockSample): Long {
        val sameBoot = previous.bootId != null && previous.bootId == now.bootId && now.elapsedMs >= previous.elapsedMs
        return when {
            sameBoot -> now.elapsedMs - previous.elapsedMs
            previous.trustedMs != null && now.trustedMs != null -> (now.trustedMs - previous.trustedMs).coerceAtLeast(0L)
            else -> (now.wallMs - previous.wallMs).coerceAtLeast(0L)
        }
    }
}

/**
 * The 15 minute pass a friend gives. It counts down in real time ([LockClock]) just like the lock.
 */
data class FriendPass(val remainingMs: Long, val lastSample: ClockSample) {
    val isActive: Boolean get() = remainingMs > 0

    /** The same pass after time has moved on to [now]; null once it has run out. */
    fun advance(now: ClockSample): FriendPass? {
        val left = remainingMs - LockClock.advanceMs(lastSample, now)
        return if (left > 0) FriendPass(left, now) else null
    }

    companion object {
        fun start(lengthMs: Long, now: ClockSample): FriendPass {
            require(lengthMs > 0) { "a pass needs a length" }
            return FriendPass(lengthMs, now)
        }
    }
}
