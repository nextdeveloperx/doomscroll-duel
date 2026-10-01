package com.doomscrollduel.domain.challenge.lock

import com.doomscrollduel.core.common.DayKeys
import com.doomscrollduel.domain.blocking.ClockSample
import com.doomscrollduel.domain.blocking.LockClock
import com.doomscrollduel.domain.challenge.ChallengeMode
import com.doomscrollduel.domain.challenge.PlayerId
import com.doomscrollduel.domain.challenge.ProGate
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/**
 * STRICT TIMER-LOCK (Pro to set up). When today's reels reach the daily limit, a lock timer starts. While it
 * runs, the reel screens of the tracked apps are blocked and the lock cannot be switched off or loosened.
 * The only exception is a friend's 15 minute pass (see `FriendPass`, `UnlockCoordinator`).
 *
 * Time is real time, not the calendar: the lock adds up elapsed time (`LockClock`), so moving the phone clock,
 * changing the time zone, killing the app or rebooting cannot shorten it.
 *
 * Honest limit: Android lets a user turn the accessibility service off or uninstall the app. The lock cannot
 * stop that; it is a commitment tool, not parental control.
 */
sealed interface LockLength {
    /** Lock until local midnight, when the new day gives a fresh limit. */
    data object UntilMidnight : LockLength

    /** Lock for a fixed number of hours (1 to 12). When it ends, a fresh allowance of one limit starts. */
    data class Hours(val hours: Int) : LockLength {
        init {
            require(hours in 1..12) { "hours must be 1..12" }
        }
    }
}

data class StrictLockConfig(
    /** The daily reel limit. Hitting it starts the lock. */
    val dailyCap: Int,
    val lockLength: LockLength,
    /** The one friend who may unlock. Null means nobody can, and the lock is absolute. */
    val unlockBuddy: PlayerId?,
    /** A raised limit waits here until the given day. Lowering the limit is immediate. */
    val pendingCap: PendingCap? = null,
) {
    init {
        require(dailyCap in MIN_CAP..MAX_CAP) { "dailyCap must be $MIN_CAP..$MAX_CAP" }
    }

    /** The limit that applies on [day]. */
    fun capOn(day: LocalDate): Int = if (pendingCap != null && !day.isBefore(pendingCap.from)) pendingCap.cap else dailyCap

    companion object {
        const val MIN_CAP = 10
        const val MAX_CAP = 1_000
    }
}

data class PendingCap(val cap: Int, val from: LocalDate)

/**
 * `Idle -> Locked -> Idle`. [Idle.baseline] is the day's total when the last lock ended; the limit counts
 * reels since then.
 */
sealed interface StrictLockState {
    data class Idle(val day: LocalDate, val baseline: Int = 0) : StrictLockState

    /**
     * [totalMs] is how long the lock lasts and [progressMs] how much of it has really elapsed. [lastSample] is
     * the reading at which [progressMs] was last brought up to date. [lockedAtWallMs] is for display only.
     */
    data class Locked(
        val day: LocalDate,
        val lockedAtWallMs: Long,
        val totalMs: Long,
        val progressMs: Long,
        val lastSample: ClockSample,
        /** Day total when the lock started. Becomes the next baseline. */
        val totalAtLock: Int,
    ) : StrictLockState {
        val remainingMs: Long get() = (totalMs - progressMs).coerceAtLeast(0L)
        val remaining: Duration get() = Duration.ofMillis(remainingMs)
    }
}

/** What the reel screens should do right now because of the timer lock. */
sealed interface LockDecision {
    data object Allow : LockDecision

    data class Block(val remaining: Duration) : LockDecision
}

/** The state brought up to date, and what it means. Persist [state]: it holds the new progress. */
data class LockEvaluation(val state: StrictLockState, val decision: LockDecision)

enum class LockReject {
    /** Switching off or changing a lock that is running. */
    LOCKED,
    NEEDS_PRO,
}

sealed interface LockChange {
    data class Accepted(val config: StrictLockConfig) : LockChange
    data class Rejected(val reason: LockReject) : LockChange
}

object StrictLock {

    /** Only Pro users can set a lock up. A lapsed Pro keeps the lock that is running but cannot start new ones. */
    fun canSetUp(isPro: Boolean) = ProGate.canCreate(ChallengeMode.STRICT_LOCK, isPro)

    fun isLocked(state: StrictLockState) = state is StrictLockState.Locked

    /** Midnight rolls an Idle state to the new day with a fresh baseline. A running lock is left alone. */
    fun rollDay(state: StrictLockState, today: LocalDate): StrictLockState =
        if (state is StrictLockState.Idle && state.day != today) StrictLockState.Idle(today, baseline = 0) else state

    /**
     * Brings [state] up to date with [now]: adds the real time that passed to a running lock and ends it when it
     * is used up. When a lock ends, the baseline moves to the total at lock time (same day) or resets (new day).
     * Safe to call as often as you like.
     */
    fun advance(state: StrictLockState, now: ClockSample, zone: ZoneId): StrictLockState {
        val today = DayKeys.dateOf(now.calendarMs, zone)
        return when (state) {
            is StrictLockState.Idle -> rollDay(state, today)
            is StrictLockState.Locked -> {
                val progress = state.progressMs + LockClock.advanceMs(state.lastSample, now)
                when {
                    progress < state.totalMs -> state.copy(progressMs = progress, lastSample = now)
                    today != state.day -> StrictLockState.Idle(today, baseline = 0)
                    else -> StrictLockState.Idle(today, baseline = state.totalAtLock)
                }
            }
        }
    }

    fun evaluate(state: StrictLockState, now: ClockSample, zone: ZoneId): LockEvaluation {
        val current = advance(state, now, zone)
        val decision = if (current is StrictLockState.Locked) LockDecision.Block(current.remaining) else LockDecision.Allow
        return LockEvaluation(current, decision)
    }

    /**
     * Called every time the counter counts a reel. [dayTotal] is today's total including that reel.
     * The reel that reaches the limit is already counted; the lock starts right after it.
     */
    fun onReelCounted(
        config: StrictLockConfig,
        state: StrictLockState,
        now: ClockSample,
        zone: ZoneId,
        dayTotal: Int,
    ): StrictLockState {
        val current = advance(state, now, zone)
        if (current !is StrictLockState.Idle) return current
        val today = DayKeys.dateOf(now.calendarMs, zone)
        if (dayTotal - current.baseline < config.capOn(today)) return current
        return StrictLockState.Locked(
            day = today,
            lockedAtWallMs = now.wallMs,
            totalMs = lockMillis(config.lockLength, now, zone),
            progressMs = 0L,
            lastSample = now,
            totalAtLock = dayTotal,
        )
    }

    /** How long a lock started at [now] lasts. For "until midnight" that is the time left today. */
    fun lockMillis(length: LockLength, now: ClockSample, zone: ZoneId): Long = when (length) {
        LockLength.UntilMidnight -> DayKeys.millisUntilNextDay(now.calendarMs, zone)
        is LockLength.Hours -> length.hours * 3_600_000L
    }

    /** Switching the mode off. Not possible while locked. */
    fun canSwitchOff(state: StrictLockState): Boolean = state is StrictLockState.Idle

    /**
     * Changing the limit. While locked: not allowed. Otherwise a LOWER limit applies at once and a HIGHER one
     * applies from tomorrow, so nobody can raise the limit right before hitting it.
     */
    fun changeCap(config: StrictLockConfig, state: StrictLockState, newCap: Int, today: LocalDate): LockChange {
        if (state !is StrictLockState.Idle) return LockChange.Rejected(LockReject.LOCKED)
        val current = config.capOn(today)
        return LockChange.Accepted(
            when {
                newCap < current -> config.copy(dailyCap = newCap, pendingCap = null)
                newCap > current -> config.copy(dailyCap = current, pendingCap = PendingCap(newCap, today.plusDays(1)))
                // Back to today's limit: a raise that was waiting for tomorrow is cancelled.
                else -> config.copy(dailyCap = current, pendingCap = null)
            },
        )
    }
}
