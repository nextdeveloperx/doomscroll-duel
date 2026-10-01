package com.doomscrollduel.domain.blocking

import com.doomscrollduel.domain.challenge.PlayerId
import com.doomscrollduel.domain.challenge.lock.LockLength
import com.doomscrollduel.domain.challenge.lock.PendingCap
import com.doomscrollduel.domain.challenge.lock.StrictLockConfig
import java.time.LocalDate

/** The friend who may unlock you. Chosen from your friends. */
data class Buddy(val uid: PlayerId, val displayName: String)

/**
 * Everything the Settings screen controls. Saved in DataStore. The defaults are what a new install gets.
 *
 * "Strict timer-lock" starts a lock when [dailyLimit] is reached. [friendUnlockEnabled] lets [buddy] give a
 * 15 minute pass. [wait10Enabled] adds a 10 second pause before reels open.
 */
data class BlockingSettings(
    val strictLockEnabled: Boolean = false,
    val lockLength: LockLength = LockLength.UntilMidnight,
    val dailyLimit: Int = DEFAULT_LIMIT,
    /** A raised limit that starts tomorrow (a lower limit is applied at once). */
    val pendingLimit: PendingCap? = null,
    val friendUnlockEnabled: Boolean = false,
    val buddy: Buddy? = null,
    val wait10Enabled: Boolean = false,
    val bedtime: BedtimeSettings = BedtimeSettings(),
    val focus: FocusSettings = FocusSettings(),
) {
    /** The limit in force on [day]. Used by the brain, the streak and the lock. */
    fun limitOn(day: LocalDate): Int =
        if (pendingLimit != null && !day.isBefore(pendingLimit.from)) pendingLimit.cap else dailyLimit

    /** The lock rules, or null when the timer-lock is off. */
    fun lockConfig(): StrictLockConfig? =
        if (!strictLockEnabled) null
        else StrictLockConfig(
            dailyCap = dailyLimit,
            lockLength = lockLength,
            unlockBuddy = buddy?.uid?.takeIf { friendUnlockEnabled },
            pendingCap = pendingLimit,
        )

    /** Can the user ask a friend for an unlock? */
    val canAskFriend: Boolean get() = friendUnlockEnabled && buddy != null

    companion object {
        const val DEFAULT_LIMIT = 100
    }
}
