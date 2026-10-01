package com.doomscrollduel.domain.challenge.lock

import com.doomscrollduel.domain.blocking.ClockSample
import com.doomscrollduel.domain.blocking.IST
import com.doomscrollduel.domain.blocking.hoursMs
import com.doomscrollduel.domain.blocking.minutesMs
import com.doomscrollduel.domain.blocking.sample
import com.doomscrollduel.domain.blocking.wall
import com.doomscrollduel.domain.challenge.AMAN
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictLockTest {
    private val day = LocalDate.of(2026, 10, 1)
    private val config = StrictLockConfig(dailyCap = 100, lockLength = LockLength.UntilMidnight, unlockBuddy = AMAN)
    private val idle = StrictLockState.Idle(day)

    /** [hour] on 1 October in India, with the monotonic clock advancing in step. */
    private fun at(hour: Int, min: Int = 0, elapsed: Long = 1_000_000L, boot: String? = "b1", d: Int = 1): ClockSample =
        sample(wall(IST, 2026, 10, d, hour, min), elapsed + (hour * 60L + min) * 60_000L, boot)

    private fun count(state: StrictLockState, total: Int, now: ClockSample, cfg: StrictLockConfig = config) =
        StrictLock.onReelCounted(cfg, state, now, IST, total)

    private fun locked(state: StrictLockState) = state as StrictLockState.Locked

    // ----- reaching the limit -------------------------------------------------------------------

    @Test
    fun `below the limit nothing happens`() {
        assertEquals(idle, count(idle, 99, at(14)))
        assertEquals(LockDecision.Allow, StrictLock.evaluate(idle, at(14), IST).decision)
    }

    @Test
    fun `the reel that reaches the limit starts the lock`() {
        val s = locked(count(idle, 100, at(14)))
        assertEquals(100, s.totalAtLock)
        assertEquals(0L, s.progressMs)
        val decision = StrictLock.evaluate(s, at(14, 1), IST).decision as LockDecision.Block
        assertEquals(Duration.ofHours(10).minusMinutes(1), decision.remaining)
    }

    @Test
    fun `until midnight lasts until local midnight`() {
        val s = locked(count(idle, 100, at(14)))
        assertEquals(hoursMs(10), s.totalMs)
    }

    @Test
    fun `hours lock lasts exactly that long`() {
        val cfg = config.copy(lockLength = LockLength.Hours(3))
        assertEquals(hoursMs(3), locked(count(idle, 100, at(14), cfg)).totalMs)
    }

    @Test
    fun `more reels while locked change nothing but the progress`() {
        val s = count(idle, 100, at(14))
        val again = locked(count(s, 140, at(15)))
        assertEquals(hoursMs(1), again.progressMs)
        assertEquals(100, again.totalAtLock)
    }

    @Test
    fun `the lock ends at midnight and the new day starts fresh`() {
        val s = count(idle, 100, at(14))
        assertTrue(StrictLock.evaluate(s, at(23, 59), IST).decision is LockDecision.Block)
        val next = StrictLock.evaluate(s, at(0, 0, d = 2, elapsed = 1_000_000L + hoursMs(24) - 14 * 3_600_000L + 14 * 3_600_000L), IST)
        assertEquals(LockDecision.Allow, next.decision)
        assertEquals(StrictLockState.Idle(LocalDate.of(2026, 10, 2), 0), next.state)
    }

    @Test
    fun `a timed lock gives a fresh allowance of one limit when it ends`() {
        val cfg = config.copy(lockLength = LockLength.Hours(2))
        val s = count(idle, 100, at(14), cfg)
        val after = StrictLock.advance(s, at(16, 1), IST) as StrictLockState.Idle
        assertEquals(100, after.baseline)
        assertEquals(after, count(after, 150, at(17), cfg)) // 50 since the lock: not yet
        assertTrue(count(after, 200, at(18), cfg) is StrictLockState.Locked)
    }

    @Test
    fun `an Idle state from yesterday rolls to today and its baseline does not leak`() {
        val yesterday = StrictLockState.Idle(LocalDate.of(2026, 9, 30), baseline = 40)
        assertEquals(StrictLockState.Idle(day, 0), StrictLock.rollDay(yesterday, day))
        assertTrue(count(yesterday, 60, at(10)) is StrictLockState.Idle)
    }

    // ----- cannot switch off ----------------------------------------------------------------------

    @Test
    fun `the lock cannot be switched off while locked`() {
        assertFalse(StrictLock.canSwitchOff(count(idle, 100, at(14))))
        assertTrue(StrictLock.canSwitchOff(idle))
    }

    @Test
    fun `the limit cannot be changed while locked`() {
        val change = StrictLock.changeCap(config, count(idle, 100, at(14)), 500, day) as LockChange.Rejected
        assertEquals(LockReject.LOCKED, change.reason)
    }

    @Test
    fun `lowering the limit applies now and raising it applies tomorrow`() {
        val lowered = (StrictLock.changeCap(config, idle, 60, day) as LockChange.Accepted).config
        assertEquals(60, lowered.capOn(day))
        val raised = (StrictLock.changeCap(config, idle, 300, day) as LockChange.Accepted).config
        assertEquals(100, raised.capOn(day))
        assertEquals(300, raised.capOn(day.plusDays(1)))
        assertTrue(count(idle, 100, at(14), raised) is StrictLockState.Locked)
    }

    @Test
    fun `lowering again cancels a pending raise`() {
        val raised = (StrictLock.changeCap(config, idle, 300, day) as LockChange.Accepted).config
        val lowered = (StrictLock.changeCap(raised, idle, 80, day) as LockChange.Accepted).config
        assertEquals(80, lowered.capOn(day.plusDays(1)))
    }

    @Test
    fun `stepping back down to today's limit cancels a raise that was waiting for tomorrow`() {
        val raised = (StrictLock.changeCap(config, idle, 300, day) as LockChange.Accepted).config
        val back = (StrictLock.changeCap(raised, idle, 100, day) as LockChange.Accepted).config
        assertEquals(null, back.pendingCap)
        assertEquals(100, back.capOn(day.plusDays(1)))
    }

    // ----- real time, not the calendar --------------------------------------------------------------

    @Test
    fun `moving the phone clock forward does not shorten the lock`() {
        val s = count(idle, 100, at(14))
        // Only 5 real minutes pass, but the user sets the clock 8 hours ahead.
        val tampered = sample(at(14).wallMs + hoursMs(8), at(14).elapsedMs + minutesMs(5), "b1")
        val eval = StrictLock.evaluate(s, tampered, IST)
        assertEquals(Duration.ofHours(10).minusMinutes(5), (eval.decision as LockDecision.Block).remaining)
    }

    @Test
    fun `moving the phone clock backward does not lengthen the lock`() {
        val s = count(idle, 100, at(14))
        val tampered = sample(at(14).wallMs - hoursMs(8), at(14).elapsedMs + hoursMs(2), "b1")
        assertEquals(Duration.ofHours(8), (StrictLock.evaluate(s, tampered, IST).decision as LockDecision.Block).remaining)
    }

    @Test
    fun `a time zone change does not change the remaining time`() {
        val s = count(idle, 100, at(14))
        val next = sample(at(14).wallMs + hoursMs(1), at(14).elapsedMs + hoursMs(1), "b1")
        val tokyo = ZoneId.of("Asia/Tokyo")
        val inIst = (StrictLock.evaluate(s, next, IST).decision as LockDecision.Block).remaining
        val inTokyo = (StrictLock.evaluate(s, next, tokyo).decision as LockDecision.Block).remaining
        assertEquals(inIst, inTokyo)
        assertEquals(Duration.ofHours(9), inIst)
    }

    @Test
    fun `progress survives a reboot using the phone clock`() {
        val s = count(idle, 100, at(14))
        val afterReboot = sample(at(14).wallMs + hoursMs(3), 8_000L, boot = "b2")
        assertEquals(Duration.ofHours(7), (StrictLock.evaluate(s, afterReboot, IST).decision as LockDecision.Block).remaining)
    }

    @Test
    fun `the lock only ever moves forward when evaluated repeatedly`() {
        var state: StrictLockState = count(idle, 100, at(14))
        var previous = Long.MAX_VALUE
        for (minute in 0..120 step 10) {
            state = StrictLock.advance(state, at(14, 0, elapsed = 1_000_000L).let { sample(it.wallMs + minute * 60_000L, it.elapsedMs + minute * 60_000L, "b1") }, IST)
            val left = locked(state).remainingMs
            assertTrue(left <= previous)
            previous = left
        }
    }

    // ----- Pro and validation -------------------------------------------------------------------------

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
    }
}
