package com.doomscrollduel.domain.challenge.lock

import com.doomscrollduel.domain.challenge.ChallengeMode
import com.doomscrollduel.domain.challenge.PlayerId
import com.doomscrollduel.domain.challenge.ProGate
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * STRICT LOCK (Pro to set up). The user sets a daily reel cap. When the cap is reached, the reel
 * screens of the tracked apps are blocked until the lock timer ends. While locked, the lock cannot be
 * switched off or loosened. The only exception is a friend unlock. It is a personal mode: no opponent,
 * no coins.
 *
 * Only the reel screens are blocked (the same surfaces the counter recognises), never a whole app.
 *
 * Honest limit: Android lets the user turn the accessibility service off or uninstall the app, and the
 * lock cannot stop that. The app shows that enforcement was lost and the lock keeps running; it is a
 * commitment tool, not a parental-control system.
 */
sealed interface LockLength {
    /** Lock until local midnight, when the new day gives a fresh cap. */
    data object UntilMidnight : LockLength

    /** Lock for a fixed number of hours (1 to 12). When it ends, a fresh allowance of one cap starts. */
    data class Hours(val hours: Int) : LockLength {
        init {
            require(hours in 1..12) { "hours must be 1..12" }
        }
    }
}

data class StrictLockConfig(
    val dailyCap: Int,
    val lockLength: LockLength,
    /** The one friend who may unlock. Null means nobody can, and the lock is absolute. */
    val unlockBuddy: PlayerId?,
    val maxFriendPassesPerLock: Int = DEFAULT_PASSES_PER_LOCK,
    /** A raised cap waits here until the given day. Lowering the cap is immediate. */
    val pendingCap: PendingCap? = null,
) {
    init {
        require(dailyCap in MIN_CAP..MAX_CAP) { "dailyCap must be $MIN_CAP..$MAX_CAP" }
        require(maxFriendPassesPerLock in 0..MAX_PASSES) { "passes per lock must be 0..$MAX_PASSES" }
    }

    /** The cap that applies on [day]. */
    fun capOn(day: LocalDate): Int = if (pendingCap != null && !day.isBefore(pendingCap.from)) pendingCap.cap else dailyCap

    companion object {
        const val MIN_CAP = 10
        const val MAX_CAP = 1_000
        const val DEFAULT_PASSES_PER_LOCK = 2
        const val MAX_PASSES = 5
        val PASS_LENGTH: Duration = Duration.ofMinutes(15)
    }
}

data class PendingCap(val cap: Int, val from: LocalDate)

/**
 * `Idle -> Locked -> (FriendPass -> Locked)* -> Idle`.
 * [Idle.baseline] is the day's total when the last lock ended; the cap counts reels since then.
 */
sealed interface StrictLockState {
    data class Idle(val day: LocalDate, val baseline: Int = 0) : StrictLockState

    data class Locked(
        val day: LocalDate,
        val lockedAt: Instant,
        val endsAt: Instant,
        /** Day total when the lock started. Becomes the next baseline. */
        val totalAtLock: Int,
        val passesUsed: Int = 0,
        /** Monotonic clock reading and boot id at lock time, to resist the user moving the phone clock. */
        val lockedAtElapsedMs: Long? = null,
        val bootId: String? = null,
    ) : StrictLockState

    /** A friend unlocked for [passUntil]. The lock timer keeps running underneath. */
    data class FriendPass(val locked: Locked, val passUntil: Instant) : StrictLockState
}

/** What the reel screens should do right now. */
sealed interface LockDecision {
    data object Allow : LockDecision

    /** Block the reel screen. [canAskFriend] says whether the "ask a friend" button is offered. */
    data class Block(val remaining: Duration, val canAskFriend: Boolean) : LockDecision
}

enum class LockReject {
    /** Switching off or changing a locked lock. */
    LOCKED,
    NEEDS_PRO,
    NO_BUDDY,
    NOT_THE_BUDDY,
    NOT_LOCKED,
    NO_PASSES_LEFT,
    PASS_ALREADY_ACTIVE,
}

sealed interface LockChange {
    data class Accepted(val config: StrictLockConfig) : LockChange
    data class Rejected(val reason: LockReject) : LockChange
}

sealed interface LockStep {
    data class Moved(val state: StrictLockState) : LockStep
    data class Rejected(val reason: LockReject) : LockStep
}

object StrictLock {

    /** Only Pro users can set a lock up. A lapsed Pro keeps the lock that is running but cannot start new ones. */
    fun canSetUp(isPro: Boolean) = ProGate.canCreate(ChallengeMode.STRICT_LOCK, isPro)

    fun isLocked(state: StrictLockState) = state !is StrictLockState.Idle

    /** Midnight rolls an Idle state to the new day with a fresh baseline. A running lock is left alone. */
    fun rollDay(state: StrictLockState, today: LocalDate): StrictLockState =
        if (state is StrictLockState.Idle && state.day != today) StrictLockState.Idle(today, baseline = 0) else state

    /**
     * Called every time the counter counts a reel. [dayTotal] is today's total including that reel.
     * The reel that crosses the cap is already counted; the lock starts right after it.
     */
    fun onReelCounted(
        config: StrictLockConfig,
        state: StrictLockState,
        now: Instant,
        zone: ZoneId,
        dayTotal: Int,
        elapsedMs: Long? = null,
        bootId: String? = null,
    ): StrictLockState {
        val today = now.atZone(zone).toLocalDate()
        val current = rollDay(state, today)
        if (current !is StrictLockState.Idle) return current
        val sinceBaseline = dayTotal - current.baseline
        if (sinceBaseline < config.capOn(today)) return current
        return StrictLockState.Locked(
            day = today,
            lockedAt = now,
            endsAt = lockEnd(config.lockLength, now, zone),
            totalAtLock = dayTotal,
            lockedAtElapsedMs = elapsedMs,
            bootId = bootId,
        )
    }

    fun lockEnd(length: LockLength, now: Instant, zone: ZoneId): Instant = when (length) {
        LockLength.UntilMidnight -> now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        is LockLength.Hours -> now.plus(Duration.ofHours(length.hours.toLong()))
    }

    /**
     * Time left on a lock. Moving the phone clock forward must not shorten it: while the phone has not
     * rebooted we also measure with the monotonic clock and trust the SMALLER elapsed time. After a reboot
     * only the wall clock is left, and a clock moved back only makes the lock longer.
     */
    fun remaining(locked: StrictLockState.Locked, now: Instant, elapsedMs: Long? = null, bootId: String? = null): Duration {
        val total = Duration.between(locked.lockedAt, locked.endsAt)
        var elapsed = Duration.between(locked.lockedAt, now).coerceAtLeast(Duration.ZERO)
        val sameBoot = bootId != null && bootId == locked.bootId
        if (sameBoot && elapsedMs != null && locked.lockedAtElapsedMs != null) {
            val mono = Duration.ofMillis((elapsedMs - locked.lockedAtElapsedMs).coerceAtLeast(0))
            if (mono < elapsed) elapsed = mono
        }
        return (total - elapsed).coerceAtLeast(Duration.ZERO)
    }

    /**
     * Moves time forward: ends a finished friend pass, and ends a finished lock (back to Idle with the
     * baseline set to the total at lock time, or to a fresh day).
     */
    fun tick(state: StrictLockState, now: Instant, zone: ZoneId, elapsedMs: Long? = null, bootId: String? = null): StrictLockState {
        val today = now.atZone(zone).toLocalDate()
        return when (state) {
            is StrictLockState.Idle -> rollDay(state, today)
            is StrictLockState.FriendPass ->
                if (now.isBefore(state.passUntil)) state else tick(state.locked, now, zone, elapsedMs, bootId)
            is StrictLockState.Locked ->
                if (remaining(state, now, elapsedMs, bootId) > Duration.ZERO) {
                    state
                } else if (state.endsAt.atZone(zone).toLocalDate() != state.day || today != state.day) {
                    StrictLockState.Idle(today, baseline = 0)
                } else {
                    StrictLockState.Idle(today, baseline = state.totalAtLock)
                }
        }
    }

    fun decide(
        config: StrictLockConfig,
        state: StrictLockState,
        now: Instant,
        elapsedMs: Long? = null,
        bootId: String? = null,
    ): LockDecision = when (state) {
        is StrictLockState.Idle -> LockDecision.Allow
        is StrictLockState.FriendPass ->
            if (now.isBefore(state.passUntil)) LockDecision.Allow else decide(config, state.locked, now, elapsedMs, bootId)
        is StrictLockState.Locked -> {
            val left = remaining(state, now, elapsedMs, bootId)
            if (left == Duration.ZERO) {
                LockDecision.Allow
            } else {
                LockDecision.Block(
                    remaining = left,
                    canAskFriend = config.unlockBuddy != null && state.passesUsed < config.maxFriendPassesPerLock,
                )
            }
        }
    }

    // ----- friend unlock ---------------------------------------------------------------------------

    /** The friend approves one 15 minute pass. Nobody else can. */
    fun friendApproves(config: StrictLockConfig, state: StrictLockState, by: PlayerId, now: Instant): LockStep {
        val locked = when (state) {
            is StrictLockState.Locked -> state
            is StrictLockState.FriendPass -> return LockStep.Rejected(LockReject.PASS_ALREADY_ACTIVE)
            is StrictLockState.Idle -> return LockStep.Rejected(LockReject.NOT_LOCKED)
        }
        val buddy = config.unlockBuddy ?: return LockStep.Rejected(LockReject.NO_BUDDY)
        if (by != buddy) return LockStep.Rejected(LockReject.NOT_THE_BUDDY)
        if (locked.passesUsed >= config.maxFriendPassesPerLock) return LockStep.Rejected(LockReject.NO_PASSES_LEFT)
        val used = locked.copy(passesUsed = locked.passesUsed + 1)
        // A pass never runs past the lock itself.
        val until = minOf(now.plus(StrictLockConfig.PASS_LENGTH), locked.endsAt)
        return LockStep.Moved(StrictLockState.FriendPass(used, until))
    }

    // ----- changing the settings -----------------------------------------------------------------

    /** Switching the mode off. Not possible while locked, with or without a pass. */
    fun canSwitchOff(state: StrictLockState): Boolean = state is StrictLockState.Idle

    /**
     * Changing the cap. While locked: not allowed. Otherwise a LOWER cap applies at once and a HIGHER cap
     * applies from tomorrow, so nobody can raise the cap right before hitting it.
     */
    fun changeCap(config: StrictLockConfig, state: StrictLockState, newCap: Int, today: LocalDate): LockChange {
        if (state !is StrictLockState.Idle) return LockChange.Rejected(LockReject.LOCKED)
        val current = config.capOn(today)
        return LockChange.Accepted(
            when {
                newCap < current -> config.copy(dailyCap = newCap, pendingCap = null)
                newCap > current -> config.copy(dailyCap = current, pendingCap = PendingCap(newCap, today.plusDays(1)))
                else -> config
            },
        )
    }
}
