package com.doomscrollduel.domain.blocking

import com.doomscrollduel.domain.challenge.lock.LockDecision
import com.doomscrollduel.domain.challenge.lock.StrictLockState
import java.time.Instant
import java.time.ZoneId

enum class BlockReason { STRICT_LOCK, BEDTIME, FOCUS }

/** What the service must do when the user is on a reel screen. */
sealed interface BlockAction {
    data object Allow : BlockAction

    /**
     * Send the user back and show the "Brain bachao" overlay. [remainingMs] is the time left (real time for the
     * lock, wall time for a window). [askQuotaLeft] is how many friend requests are left (0 hides the button).
     */
    data class Block(
        val reason: BlockReason,
        val remainingMs: Long,
        val canAskFriend: Boolean,
        val askQuotaLeft: Int,
        val askPending: Boolean,
    ) : BlockAction

    /** Show the 10 second wait card. */
    data class Gate(val state: GateState) : BlockAction
}

/** Everything the decision needs, already brought up to date. */
data class BlockingInput(
    val settings: BlockingSettings,
    val lock: LockDecision,
    val pass: FriendPass?,
    val window: ActiveWindow?,
    val gate: GateSession?,
    val nowElapsedMs: Long,
    val nowMs: Long,
    val askQuotaLeft: Int,
    val askPending: Boolean,
)

/**
 * The priority order, in one place:
 *
 *  1. A friend pass is running  -> Allow (for the lock, bedtime and focus).
 *  2. The timer-lock is running -> Block (STRICT_LOCK).
 *  3. A bedtime or focus window -> Block (the reel limit is zero).
 *  4. The wait-10 gate is on    -> Gate, until the user has passed it this session.
 *  5. Otherwise                 -> Allow.
 */
object BlockingEngine {

    fun decide(input: BlockingInput): BlockAction {
        if (input.pass?.isActive == true) return BlockAction.Allow

        val canAsk = input.settings.canAskFriend
        val lock = input.lock
        if (lock is LockDecision.Block) {
            return BlockAction.Block(BlockReason.STRICT_LOCK, lock.remaining.toMillis(), canAsk && input.askQuotaLeft > 0 && !input.askPending, input.askQuotaLeft, input.askPending)
        }
        val window = input.window
        if (window != null) {
            val reason = if (window.kind == WindowKind.BEDTIME) BlockReason.BEDTIME else BlockReason.FOCUS
            val left = (window.end.toEpochMilli() - input.nowMs).coerceAtLeast(0L)
            return BlockAction.Block(reason, left, canAsk && input.askQuotaLeft > 0 && !input.askPending, input.askQuotaLeft, input.askPending)
        }
        if (input.settings.wait10Enabled) {
            val state = input.gate?.state(input.nowElapsedMs) ?: GateState.Counting(WaitGate.COUNTDOWN_MS)
            return if (state == GateState.Passed) BlockAction.Allow else BlockAction.Gate(state)
        }
        return BlockAction.Allow
    }

    /** Is there anything that could block or gate right now? When not, the service skips checking the screen at all. */
    fun isWatching(settings: BlockingSettings, lock: StrictLockState, pass: FriendPass?, now: Instant, zone: ZoneId): Boolean {
        if (pass?.isActive == true) return false
        return settings.wait10Enabled ||
            lock is StrictLockState.Locked ||
            TimeWindows.active(settings.bedtime, settings.focus, now, zone) != null
    }
}
