package com.doomscrollduel.domain.properties

import com.doomscrollduel.domain.blocking.ClockSample
import com.doomscrollduel.domain.blocking.IST
import com.doomscrollduel.domain.blocking.hoursMs
import com.doomscrollduel.domain.blocking.sample
import com.doomscrollduel.domain.blocking.wall
import com.doomscrollduel.domain.challenge.AMAN
import com.doomscrollduel.domain.challenge.lock.LockDecision
import com.doomscrollduel.domain.challenge.lock.LockLength
import com.doomscrollduel.domain.challenge.lock.StrictLock
import com.doomscrollduel.domain.challenge.lock.StrictLockConfig
import com.doomscrollduel.domain.challenge.lock.StrictLockState
import java.time.LocalDate
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lock timer under a person who keeps changing the phone's clock, rebooting and waiting. Rules under test:
 *  - on the same boot only real (monotonic) time counts, whatever the calendar clock says;
 *  - the remaining time never goes up, never goes below zero, and the lock always ends once enough real time passed;
 *  - a reboot never adds time to a lock.
 */
class LockTimerPropertyTest {
    private val day = LocalDate.of(2026, 10, 1)
    private val config = StrictLockConfig(dailyCap = 100, lockLength = LockLength.Hours(3), unlockBuddy = AMAN)

    private fun lockedAt(wallMs: Long, elapsed: Long): StrictLockState.Locked {
        val s = StrictLock.onReelCounted(config, StrictLockState.Idle(day), sample(wallMs, elapsed, "b1"), IST, 100)
        return s as StrictLockState.Locked
    }

    @Test
    fun `same boot, any clock tampering, remaining equals total minus real time`() {
        for (seed in 1..600) {
            val rnd = Random(seed)
            val wall0 = wall(IST, 2026, 10, 1, 14)
            var state: StrictLockState = lockedAt(wall0, 1_000_000L)
            var wallNow = wall0
            var elapsed = 1_000_000L
            var realPassed = 0L
            var lastRemaining = hoursMs(3)

            repeat(25) {
                val real = rnd.nextLong(0, 40 * 60_000L)
                elapsed += real
                realPassed += real
                wallNow += real + rnd.nextLong(-3 * 86_400_000L, 3 * 86_400_000L) // the user also moves the clock
                val e = StrictLock.evaluate(state, ClockSample(wallNow, elapsed, "b1"), IST)
                state = e.state
                val remaining = (e.decision as? LockDecision.Block)?.remaining?.toMillis() ?: 0L
                assertTrue("remaining went up, seed=$seed", remaining <= lastRemaining)
                assertTrue(remaining >= 0)
                assertEquals("seed=$seed", maxOf(0L, hoursMs(3) - realPassed), remaining)
                lastRemaining = remaining
            }
        }
    }

    @Test
    fun `the lock always ends once enough real time has passed`() {
        for (seed in 1..300) {
            val rnd = Random(seed)
            val wall0 = wall(IST, 2026, 10, 1, 14)
            var state: StrictLockState = lockedAt(wall0, 1_000_000L)
            var elapsed = 1_000_000L
            var wallNow = wall0
            while (state is StrictLockState.Locked) {
                val real = rnd.nextLong(1, 30 * 60_000L)
                elapsed += real
                wallNow += real + rnd.nextLong(-86_400_000L, 86_400_000L)
                state = StrictLock.evaluate(state, ClockSample(wallNow, elapsed, "b1"), IST).state
                assertTrue("lock lasted longer than 3 hours of real time, seed=$seed", elapsed - 1_000_000L <= hoursMs(3) + 30 * 60_000L)
            }
        }
    }

    @Test
    fun `rebooting never adds time to a lock, even with a tampered clock`() {
        for (seed in 1..500) {
            val rnd = Random(seed)
            val wall0 = wall(IST, 2026, 10, 1, 14)
            val locked = lockedAt(wall0, 1_000_000L)
            // The phone is off for [off] ms; the user may also have moved the calendar clock before switching on.
            val off = rnd.nextLong(0, 5 * 3_600_000L)
            val tamper = rnd.nextLong(-2 * 86_400_000L, 0) // backwards (a forward move is a documented gap, see LockClock)
            val after = StrictLock.evaluate(locked, ClockSample(wall0 + off + tamper, 5_000L, "b2"), IST)
            val remaining = (after.decision as? LockDecision.Block)?.remaining?.toMillis() ?: 0L
            assertTrue("reboot added time, seed=$seed", remaining <= locked.remainingMs)
        }
    }

    @Test
    fun `evaluating again and again with the same reading changes nothing`() {
        val wall0 = wall(IST, 2026, 10, 1, 14)
        val locked = lockedAt(wall0, 1_000_000L)
        val now = ClockSample(wall0 + 600_000L, 1_600_000L, "b1")
        val once = StrictLock.evaluate(locked, now, IST)
        repeat(20) { assertEquals(once, StrictLock.evaluate(once.state, now, IST)) }
    }

    @Test
    fun `a lock that ends gives a fresh allowance and starts again only at the next limit`() {
        val wall0 = wall(IST, 2026, 10, 1, 14)
        var state: StrictLockState = lockedAt(wall0, 1_000_000L)
        state = StrictLock.evaluate(state, ClockSample(wall0 + hoursMs(3) + 1, 1_000_000L + hoursMs(3) + 1, "b1"), IST).state
        assertTrue(state is StrictLockState.Idle)
        val now = ClockSample(wall0 + hoursMs(3) + 2, 1_000_000L + hoursMs(3) + 2, "b1")
        // 100 reels was the total at the first lock; 199 more is still under the next limit of 100 since then.
        assertTrue(StrictLock.onReelCounted(config, state, now, IST, 199) is StrictLockState.Idle)
        assertTrue(StrictLock.onReelCounted(config, state, now, IST, 200) is StrictLockState.Locked)
    }
}
