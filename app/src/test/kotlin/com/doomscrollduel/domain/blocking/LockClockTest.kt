package com.doomscrollduel.domain.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LockClockTest {
    private val start = sample(wallMs = 1_000_000_000_000L, elapsedMs = 50_000L)

    @Test
    fun `same boot uses only the monotonic clock`() {
        val later = sample(wallMs = start.wallMs + 5_000L, elapsedMs = start.elapsedMs + 5_000L)
        assertEquals(5_000L, LockClock.advanceMs(start, later))
    }

    @Test
    fun `moving the phone clock forward changes nothing on the same boot`() {
        val tampered = sample(wallMs = start.wallMs + hoursMs(8), elapsedMs = start.elapsedMs + 60_000L)
        assertEquals(60_000L, LockClock.advanceMs(start, tampered))
    }

    @Test
    fun `moving the phone clock backward changes nothing on the same boot`() {
        val tampered = sample(wallMs = start.wallMs - hoursMs(8), elapsedMs = start.elapsedMs + 60_000L)
        assertEquals(60_000L, LockClock.advanceMs(start, tampered))
    }

    @Test
    fun `after a reboot the phone clock is used`() {
        val rebooted = sample(wallMs = start.wallMs + minutesMs(30), elapsedMs = 4_000L, boot = "boot-2")
        assertEquals(minutesMs(30), LockClock.advanceMs(start, rebooted))
    }

    @Test
    fun `after a reboot trusted time wins over a tampered phone clock`() {
        val before = sample(start.wallMs, start.elapsedMs, trusted = 5_000_000L)
        // The phone clock says 8 hours passed, but the server clock says 30 minutes.
        val after = sample(start.wallMs + hoursMs(8), 4_000L, boot = "boot-2", trusted = 5_000_000L + minutesMs(30))
        assertEquals(minutesMs(30), LockClock.advanceMs(before, after))
    }

    @Test
    fun `a clock that went backwards across a reboot counts as zero, never negative`() {
        val rebooted = sample(wallMs = start.wallMs - hoursMs(2), elapsedMs = 4_000L, boot = "boot-2")
        assertEquals(0L, LockClock.advanceMs(start, rebooted))
    }

    @Test
    fun `an unknown boot id is treated like a reboot`() {
        val prev = sample(start.wallMs, start.elapsedMs, boot = null)
        val now = sample(start.wallMs + 1_000L, start.elapsedMs + 999_999L, boot = null)
        assertEquals(1_000L, LockClock.advanceMs(prev, now)) // uses wall: 1000, not elapsed: 999999
    }

    @Test
    fun `elapsed that went backwards on the same boot id falls back to the phone clock`() {
        val broken = sample(start.wallMs + 7_000L, start.elapsedMs - 1L)
        assertEquals(7_000L, LockClock.advanceMs(start, broken))
    }

    @Test
    fun `calendar time prefers trusted time`() {
        assertEquals(42L, sample(1L, 1L, trusted = 42L).calendarMs)
        assertEquals(1L, sample(1L, 1L).calendarMs)
    }
}

class FriendPassTest {
    private val t0 = sample(1_000_000_000_000L, 10_000L)

    @Test
    fun `a pass counts down in real time and then ends`() {
        val pass = FriendPass.start(minutesMs(15), t0)
        assertTrue(pass.isActive)
        val after10 = pass.advance(sample(t0.wallMs + minutesMs(10), t0.elapsedMs + minutesMs(10)))!!
        assertEquals(minutesMs(5), after10.remainingMs)
        assertNull(after10.advance(sample(t0.wallMs + minutesMs(15), t0.elapsedMs + minutesMs(15))))
    }

    @Test
    fun `changing the phone clock cannot extend or cut a pass`() {
        val pass = FriendPass.start(minutesMs(15), t0)
        val forward = sample(t0.wallMs + hoursMs(5), t0.elapsedMs + minutesMs(5))
        assertEquals(minutesMs(10), pass.advance(forward)!!.remainingMs)
        val back = sample(t0.wallMs - hoursMs(5), t0.elapsedMs + minutesMs(5))
        assertEquals(minutesMs(10), pass.advance(back)!!.remainingMs)
    }

    @Test
    fun `a pass needs a length`() {
        try {
            FriendPass.start(0, t0)
            throw AssertionError("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }
}
