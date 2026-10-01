package com.doomscrollduel.domain.blocking

import com.doomscrollduel.domain.challenge.AMAN
import com.doomscrollduel.domain.challenge.lock.LockLength
import com.doomscrollduel.domain.challenge.lock.PendingCap
import com.doomscrollduel.domain.challenge.lock.StrictLockState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockingCodecTest {
    private val day = LocalDate.of(2026, 10, 1)
    private val s0 = ClockSample(1_790_000_000_000L, 123_456L, "boot-7", 1_790_000_000_500L)

    @Test
    fun `settings survive a round trip with every field`() {
        val settings = BlockingSettings(
            strictLockEnabled = true,
            lockLength = LockLength.Hours(3),
            dailyLimit = 80,
            pendingLimit = PendingCap(150, day.plusDays(1)),
            friendUnlockEnabled = true,
            buddy = Buddy(AMAN, "Aman"),
            wait10Enabled = true,
            bedtime = BedtimeSettings(true, TimeRange(LocalTime.of(22, 30), LocalTime.of(5, 30))),
            focus = FocusSettings(
                true,
                mapOf(
                    DayOfWeek.MONDAY to listOf(TimeRange(LocalTime.of(9, 0), LocalTime.of(11, 0)), TimeRange(LocalTime.of(16, 0), LocalTime.of(18, 0))),
                    DayOfWeek.FRIDAY to listOf(TimeRange(LocalTime.of(22, 0), LocalTime.of(2, 0))),
                ),
            ),
        )
        assertEquals(settings, BlockingCodec.decodeSettings(BlockingCodec.encodeSettings(settings)))
    }

    @Test
    fun `default settings round trip and a fresh install gets the defaults`() {
        assertEquals(BlockingSettings(), BlockingCodec.decodeSettings(BlockingCodec.encodeSettings(BlockingSettings())))
        assertEquals(BlockingSettings(), BlockingCodec.decodeSettings(null))
        assertEquals(BlockingSettings(), BlockingCodec.decodeSettings(""))
    }

    @Test
    fun `damaged settings fall back to defaults instead of crashing`() {
        assertEquals(BlockingSettings(), BlockingCodec.decodeSettings("{ this is not json"))
        assertEquals(BlockingSettings(), BlockingCodec.decodeSettings("""{"bedtime":{"start":"99:99","end":"06:00"}}"""))
    }

    @Test
    fun `settings written by a newer version still load`() {
        val text = """{"schema":2,"strictLock":true,"dailyLimit":60,"somethingNew":{"a":1}}"""
        val s = BlockingCodec.decodeSettings(text)
        assertTrue(s.strictLockEnabled)
        assertEquals(60, s.dailyLimit)
    }

    @Test
    fun `a running lock a pass and the request history survive a round trip`() {
        val state = BlockingState(
            lock = StrictLockState.Locked(day, 1_790_000_000_000L, 36_000_000L, 1_200_000L, s0, 100),
            pass = FriendPass(600_000L, s0),
            unlockRequestTimesMs = listOf(1L, 2L, 3L),
            pendingUnlock = PendingUnlock("req-1", 3L),
        )
        assertEquals(state, BlockingCodec.decodeState(BlockingCodec.encodeState(state)))
    }

    @Test
    fun `an idle lock round trips`() {
        val state = BlockingState(lock = StrictLockState.Idle(day, 42))
        assertEquals(state, BlockingCodec.decodeState(BlockingCodec.encodeState(state)))
    }

    @Test
    fun `damaged or missing state falls back to idle`() {
        assertEquals(BlockingState(), BlockingCodec.decodeState(null))
        assertEquals(BlockingState(), BlockingCodec.decodeState("garbage"))
    }

    @Test
    fun `a locked state with no clock reading is read as idle rather than invented`() {
        val text = """{"lock":{"type":"locked","day":"2026-10-01","totalMs":1000}}"""
        assertTrue(BlockingCodec.decodeState(text).lock is StrictLockState.Idle)
    }
}
