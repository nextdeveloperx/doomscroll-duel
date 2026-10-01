package com.doomscrollduel.domain.challenge.lock

import com.doomscrollduel.domain.challenge.AMAN
import com.doomscrollduel.domain.challenge.RIYA
import com.doomscrollduel.domain.challenge.ROHAN
import com.doomscrollduel.domain.challenge.hours
import com.doomscrollduel.domain.challenge.minutes
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictLockTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private val day = LocalDate.of(2026, 10, 1)
    private fun at(h: Int, min: Int = 0, d: Int = 1): Instant = ZonedDateTime.of(2026, 10, d, h, min, 0, 0, ist).toInstant()

    private val config = StrictLockConfig(dailyCap = 100, lockLength = LockLength.UntilMidnight, unlockBuddy = AMAN)
    private val idle = StrictLockState.Idle(day)

    private fun count(state: StrictLockState, total: Int, now: Instant, cfg: StrictLockConfig = config) =
        StrictLock.onReelCounted(cfg, state, now, ist, total)

    private fun locked(state: StrictLockState) = state as StrictLockState.Locked

    // ----- reaching the cap ---------------------------------------------------------------------

    @Test
    fun `below the cap nothing happens`() {
        assertEquals(idle, count(idle, 99, at(14)))
        assertEquals(LockDecision.Allow, StrictLock.decide(config, idle, at(14)))
    }

    @Test
    fun `the reel that reaches the cap starts the lock`() {
        val s = locked(count(idle, 100, at(14)))
        assertEquals(at(14), s.lockedAt)
        assertEquals(100, s.totalAtLock)
        val decision = StrictLock.decide(config, s, at(14, 1)) as LockDecision.Block
        assertTrue(decision.canAskFriend)
    }

    @Test
    fun `until midnight lasts until local midnight`() {
        val s = locked(count(idle, 100, at(14)))
        assertEquals(at(0, d = 2), s.endsAt)
        assertEquals(hours(10), StrictLock.remaining(s, at(14)))
    }

    @Test
    fun `hours lock lasts exactly that long`() {
        val cfg = config.copy(lockLength = LockLength.Hours(3))
        val s = locked(count(idle, 100, at(14), cfg))
        assertEquals(at(17), s.endsAt)
    }

    @Test
    fun `more reels while locked change nothing`() {
        val s = count(idle, 100, at(14))
        assertEquals(s, count(s, 140, at(15)))
    }

    @Test
    fun `the lock ends at midnight and the new day starts fresh`() {
        val s = count(idle, 100, at(14))
        assertTrue(StrictLock.decide(config, s, at(23, 59)) is LockDecision.Block)
        assertEquals(LockDecision.Allow, StrictLock.decide(config, s, at(0, 0, d = 2)))
        assertEquals(StrictLockState.Idle(LocalDate.of(2026, 10, 2), baseline = 0), StrictLock.tick(s, at(0, 0, d = 2), ist))
    }

    @Test
    fun `a timed lock gives a fresh allowance of one cap when it ends`() {
        val cfg = config.copy(lockLength = LockLength.Hours(2))
        val s = count(idle, 100, at(14), cfg)
        val after = StrictLock.tick(s, at(16), ist) as StrictLockState.Idle
        assertEquals(100, after.baseline)
        // 150 reels today is only 50 since the lock, so no new lock yet.
        assertEquals(after, count(after, 150, at(17), cfg))
        // 200 is a full cap since the lock: lock again.
        assertTrue(count(after, 200, at(18), cfg) is StrictLockState.Locked)
    }

    @Test
    fun `an Idle state from yesterday rolls to today`() {
        val yesterday = StrictLockState.Idle(LocalDate.of(2026, 9, 30), baseline = 40)
        assertEquals(StrictLockState.Idle(day, 0), StrictLock.rollDay(yesterday, day))
        // Yesterday's baseline must not leak into today: 60 reels today is below the cap of 100.
        assertTrue(count(yesterday, 60, at(10)) is StrictLockState.Idle)
    }

    // ----- cannot switch off ---------------------------------------------------------------------

    @Test
    fun `the lock cannot be switched off while locked or on a friend pass`() {
        val s = count(idle, 100, at(14))
        assertFalse(StrictLock.canSwitchOff(s))
        val pass = (StrictLock.friendApproves(config, s, AMAN, at(15)) as LockStep.Moved).state
        assertFalse(StrictLock.canSwitchOff(pass))
        assertTrue(StrictLock.canSwitchOff(idle))
    }

    @Test
    fun `the cap cannot be changed while locked`() {
        val s = count(idle, 100, at(14))
        val change = StrictLock.changeCap(config, s, 500, day) as LockChange.Rejected
        assertEquals(LockReject.LOCKED, change.reason)
    }

    @Test
    fun `lowering the cap applies now and raising it applies tomorrow`() {
        val lowered = (StrictLock.changeCap(config, idle, 60, day) as LockChange.Accepted).config
        assertEquals(60, lowered.capOn(day))
        val raised = (StrictLock.changeCap(config, idle, 300, day) as LockChange.Accepted).config
        assertEquals(100, raised.capOn(day))
        assertEquals(300, raised.capOn(day.plusDays(1)))
        // Raising the cap right before hitting it does not help today.
        assertTrue(count(idle, 100, at(14), raised) is StrictLockState.Locked)
    }

    @Test
    fun `lowering again cancels a pending raise`() {
        val raised = (StrictLock.changeCap(config, idle, 300, day) as LockChange.Accepted).config
        val lowered = (StrictLock.changeCap(raised, idle, 80, day) as LockChange.Accepted).config
        assertEquals(80, lowered.capOn(day.plusDays(1)))
    }

    // ----- friend unlock ---------------------------------------------------------------------------

    private val lockedState get() = count(idle, 100, at(14))

    @Test
    fun `only the chosen friend can unlock, for 15 minutes`() {
        val s = lockedState
        assertEquals(LockReject.NOT_THE_BUDDY, (StrictLock.friendApproves(config, s, RIYA, at(15)) as LockStep.Rejected).reason)
        assertEquals(LockReject.NOT_THE_BUDDY, (StrictLock.friendApproves(config, s, ROHAN, at(15)) as LockStep.Rejected).reason)
        val pass = (StrictLock.friendApproves(config, s, AMAN, at(15)) as LockStep.Moved).state as StrictLockState.FriendPass
        assertEquals(at(15, 15), pass.passUntil)
        assertEquals(LockDecision.Allow, StrictLock.decide(config, pass, at(15, 10)))
    }

    @Test
    fun `after the pass the lock is back`() {
        val pass = (StrictLock.friendApproves(config, lockedState, AMAN, at(15)) as LockStep.Moved).state
        assertTrue(StrictLock.decide(config, pass, at(15, 15)) is LockDecision.Block)
        val back = StrictLock.tick(pass, at(15, 15), ist)
        assertTrue(back is StrictLockState.Locked)
        assertEquals(1, locked(back).passesUsed)
    }

    @Test
    fun `two passes per lock and then none`() {
        var s: StrictLockState = lockedState
        for (hour in listOf(15, 17)) {
            s = (StrictLock.friendApproves(config, s, AMAN, at(hour)) as LockStep.Moved).state
            s = StrictLock.tick(s, at(hour, 20), ist)
        }
        assertEquals(LockReject.NO_PASSES_LEFT, (StrictLock.friendApproves(config, s, AMAN, at(19)) as LockStep.Rejected).reason)
        val decision = StrictLock.decide(config, s, at(19)) as LockDecision.Block
        assertFalse(decision.canAskFriend)
    }

    @Test
    fun `a second pass cannot stack on a running pass`() {
        val pass = (StrictLock.friendApproves(config, lockedState, AMAN, at(15)) as LockStep.Moved).state
        assertEquals(LockReject.PASS_ALREADY_ACTIVE, (StrictLock.friendApproves(config, pass, AMAN, at(15, 5)) as LockStep.Rejected).reason)
    }

    @Test
    fun `no buddy means no exception at all`() {
        val noBuddy = config.copy(unlockBuddy = null)
        val s = count(idle, 100, at(14), noBuddy)
        assertEquals(LockReject.NO_BUDDY, (StrictLock.friendApproves(noBuddy, s, AMAN, at(15)) as LockStep.Rejected).reason)
        assertFalse((StrictLock.decide(noBuddy, s, at(15)) as LockDecision.Block).canAskFriend)
    }

    @Test
    fun `nothing to unlock when not locked`() {
        assertEquals(LockReject.NOT_LOCKED, (StrictLock.friendApproves(config, idle, AMAN, at(15)) as LockStep.Rejected).reason)
    }

    @Test
    fun `a pass never runs past the end of the lock`() {
        val cfg = config.copy(lockLength = LockLength.Hours(1))
        val s = count(idle, 100, at(14), cfg)
        val pass = (StrictLock.friendApproves(cfg, s, AMAN, at(14, 50)) as LockStep.Moved).state as StrictLockState.FriendPass
        assertEquals(at(15), pass.passUntil)
    }

    // ----- clock tampering and reboot --------------------------------------------------------------

    private val boot = "boot-1"

    private fun lockedWithClock(): StrictLockState.Locked =
        locked(StrictLock.onReelCounted(config, idle, at(14), ist, 100, elapsedMs = 1_000_000L, bootId = boot))

    @Test
    fun `moving the phone clock forward does not shorten the lock`() {
        val s = lockedWithClock()
        // The user sets the clock 8 hours ahead, but only 5 real minutes passed.
        val tampered = at(22)
        val remaining = StrictLock.remaining(s, tampered, elapsedMs = 1_000_000L + 5 * 60_000, bootId = boot)
        assertEquals(hours(10).minus(minutes(5)), remaining)
        assertTrue(StrictLock.decide(config, s, tampered, elapsedMs = 1_000_000L + 5 * 60_000, bootId = boot) is LockDecision.Block)
    }

    @Test
    fun `without a monotonic reading the wall clock is used`() {
        val s = lockedWithClock()
        assertEquals(hours(2), StrictLock.remaining(s, at(22)))
    }

    @Test
    fun `after a reboot the wall clock is used`() {
        val s = lockedWithClock()
        assertEquals(hours(2), StrictLock.remaining(s, at(22), elapsedMs = 5_000L, bootId = "boot-2"))
    }

    @Test
    fun `moving the clock back only makes the lock longer or equal, never shorter`() {
        val s = lockedWithClock()
        val earlier = at(10)
        assertEquals(hours(10), StrictLock.remaining(s, earlier)) // elapsed clamps to zero
    }

    @Test
    fun `a lock survives a restart because the state is plain data`() {
        val s = lockedWithClock()
        val restored = s.copy()
        assertEquals(StrictLock.remaining(s, at(20)), StrictLock.remaining(restored, at(20)))
    }

    // ----- Pro ------------------------------------------------------------------------------------

    @Test
    fun `only Pro users can set up a lock`() {
        assertTrue(StrictLock.canSetUp(isPro = true))
        assertFalse(StrictLock.canSetUp(isPro = false))
    }

    @Test
    fun `config validation`() {
        val bad = listOf<() -> Unit>(
            { StrictLockConfig(9, LockLength.UntilMidnight, null) },
            { StrictLockConfig(1_001, LockLength.UntilMidnight, null) },
            { StrictLockConfig(100, LockLength.UntilMidnight, null, maxFriendPassesPerLock = 6) },
            { LockLength.Hours(0) },
            { LockLength.Hours(13) },
        )
        for (f in bad) {
            try {
                f()
                throw AssertionError("expected IllegalArgumentException")
            } catch (_: IllegalArgumentException) {
            }
        }
        assertEquals(Duration.ofMinutes(15), StrictLockConfig.PASS_LENGTH)
    }
}
