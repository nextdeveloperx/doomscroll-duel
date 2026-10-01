package com.doomscrollduel.domain.blocking

import com.doomscrollduel.domain.challenge.lock.LockLength
import com.doomscrollduel.domain.challenge.lock.StrictLock
import com.doomscrollduel.domain.challenge.lock.StrictLockConfig
import com.doomscrollduel.domain.challenge.lock.StrictLockState
import java.time.LocalDate

/** Every change the Settings screen can ask for. */
sealed interface SettingChange {
    data class StrictLockEnabled(val on: Boolean) : SettingChange
    data class LockLengthChanged(val length: LockLength) : SettingChange
    data class DailyLimitChanged(val limit: Int) : SettingChange
    data class FriendUnlockEnabled(val on: Boolean) : SettingChange
    data class BuddyChanged(val buddy: Buddy?) : SettingChange
    data class Wait10Enabled(val on: Boolean) : SettingChange
    data class BedtimeEnabled(val on: Boolean) : SettingChange
    data class BedtimeRangeChanged(val range: TimeRange) : SettingChange
    data class FocusEnabled(val on: Boolean) : SettingChange
    data class FocusChanged(val focus: FocusSettings) : SettingChange
}

enum class ChangeBlocker {
    /** The timer-lock is running. */
    LOCK_RUNNING,

    /** A bedtime or focus window is running right now. */
    WINDOW_RUNNING,

    /** The limit must be 10 to 1000. */
    OUT_OF_RANGE,

    /** Friend unlock needs a friend chosen first. */
    NEEDS_BUDDY,
}

sealed interface ChangeResult {
    data class Applied(val settings: BlockingSettings) : ChangeResult

    /** [until] is how long it stays blocked, when known. */
    data class Refused(val blocker: ChangeBlocker, val untilMs: Long? = null) : ChangeResult
}

/**
 * The commitment rule: TIGHTENING is always allowed, LOOSENING is refused while the thing it loosens is running.
 * That is what makes a lock a lock: you cannot switch it off, shorten it, widen the limit or add an exception
 * in the middle of it. The rule is applied the same way for the timer-lock, bedtime and focus hours.
 *
 * | Change | While the timer-lock runs | While a bedtime or focus window runs |
 * |---|---|---|
 * | Switch the timer-lock off, shorten it | refused | allowed |
 * | Raise the daily limit | refused (a raise starts tomorrow anyway) | n/a |
 * | Turn friend unlock ON or change the friend | refused | refused |
 * | Turn friend unlock OFF | allowed (tighter) | allowed |
 * | Turn bedtime or focus OFF, or edit them | allowed | refused for the window that is running |
 * | Wait-10 gate | allowed | allowed |
 */
object SettingsPolicy {

    fun apply(
        settings: BlockingSettings,
        change: SettingChange,
        lock: StrictLockState,
        window: ActiveWindow?,
        today: LocalDate,
        nowMs: Long,
    ): ChangeResult {
        val lockRunning = StrictLock.isLocked(lock)
        val lockLeftMs = (lock as? StrictLockState.Locked)?.remainingMs
        val windowLeftMs = window?.let { it.end.toEpochMilli() - nowMs }
        fun refusedByLock() = ChangeResult.Refused(ChangeBlocker.LOCK_RUNNING, lockLeftMs)
        fun refusedByWindow() = ChangeResult.Refused(ChangeBlocker.WINDOW_RUNNING, windowLeftMs)
        fun ok(s: BlockingSettings) = ChangeResult.Applied(s)

        return when (change) {
            is SettingChange.StrictLockEnabled ->
                if (!change.on && lockRunning) refusedByLock() else ok(settings.copy(strictLockEnabled = change.on))

            is SettingChange.LockLengthChanged ->
                if (lockRunning) refusedByLock() else ok(settings.copy(lockLength = change.length))

            is SettingChange.DailyLimitChanged -> dailyLimit(settings, change.limit, lock, today, lockRunning, ::refusedByLock)

            is SettingChange.FriendUnlockEnabled -> when {
                change.on && settings.buddy == null -> ChangeResult.Refused(ChangeBlocker.NEEDS_BUDDY)
                change.on && lockRunning -> refusedByLock()
                change.on && window != null -> refusedByWindow()
                else -> ok(settings.copy(friendUnlockEnabled = change.on))
            }

            is SettingChange.BuddyChanged -> when {
                lockRunning -> refusedByLock()
                window != null -> refusedByWindow()
                else -> ok(settings.copy(buddy = change.buddy, friendUnlockEnabled = settings.friendUnlockEnabled && change.buddy != null))
            }

            is SettingChange.Wait10Enabled -> ok(settings.copy(wait10Enabled = change.on))

            is SettingChange.BedtimeEnabled ->
                if (!change.on && window?.kind == WindowKind.BEDTIME) refusedByWindow()
                else ok(settings.copy(bedtime = settings.bedtime.copy(enabled = change.on)))

            is SettingChange.BedtimeRangeChanged ->
                if (window?.kind == WindowKind.BEDTIME) refusedByWindow()
                else ok(settings.copy(bedtime = settings.bedtime.copy(range = change.range)))

            is SettingChange.FocusEnabled ->
                if (!change.on && window?.kind == WindowKind.FOCUS) refusedByWindow()
                else ok(settings.copy(focus = settings.focus.copy(enabled = change.on)))

            is SettingChange.FocusChanged ->
                if (window?.kind == WindowKind.FOCUS) refusedByWindow()
                else ok(settings.copy(focus = change.focus.copy(enabled = settings.focus.enabled)))
        }
    }

    private fun dailyLimit(
        settings: BlockingSettings,
        newLimit: Int,
        lock: StrictLockState,
        today: LocalDate,
        lockRunning: Boolean,
        refusedByLock: () -> ChangeResult,
    ): ChangeResult {
        if (newLimit !in StrictLockConfig.MIN_CAP..StrictLockConfig.MAX_CAP) {
            return ChangeResult.Refused(ChangeBlocker.OUT_OF_RANGE)
        }
        if (lockRunning) return refusedByLock()
        val config = StrictLockConfig(settings.dailyLimit, settings.lockLength, null, settings.pendingLimit)
        val change = StrictLock.changeCap(config, lock, newLimit, today) as com.doomscrollduel.domain.challenge.lock.LockChange.Accepted
        return ChangeResult.Applied(settings.copy(dailyLimit = change.config.dailyCap, pendingLimit = change.config.pendingCap))
    }
}
