package com.doomscrollduel.core.common

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DayClockTest {
    private val ist = ZoneId.of("Asia/Kolkata")

    private fun millis(zone: ZoneId, y: Int, m: Int, d: Int, h: Int, min: Int, s: Int = 0) =
        ZonedDateTime.of(y, m, d, h, min, s, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `date follows the local zone not UTC`() {
        // 2026-10-01 20:00 UTC is already 1:30 on the 2nd in India.
        val utcMillis = millis(ZoneOffset.UTC, 2026, 10, 1, 20, 0)
        assertEquals(LocalDate.of(2026, 10, 2), DayKeys.dateOf(utcMillis, ist))
        assertEquals(LocalDate.of(2026, 10, 1), DayKeys.dateOf(utcMillis, ZoneOffset.UTC))
    }

    @Test
    fun `millis until midnight`() {
        val now = millis(ist, 2026, 10, 1, 23, 59, 30)
        assertEquals(30_000L, DayKeys.millisUntilNextDay(now, ist))
    }

    @Test
    fun `a 23 hour day (spring forward) still ends at local midnight`() {
        val ny = ZoneId.of("America/New_York")
        val now = millis(ny, 2026, 3, 8, 0, 0) // clocks jump at 2am, the day has 23 hours
        assertEquals(23 * 3_600_000L, DayKeys.millisUntilNextDay(now, ny))
    }

    @Test
    fun `emits the new date at local midnight`() = runTest {
        val start = millis(ist, 2026, 10, 1, 23, 59, 50)
        val clock = DayClock(nowMillis = { start + testScheduler.currentTime }, zone = { ist })
        val seen = mutableListOf<LocalDate>()
        val job = backgroundScope.launch { clock.today.collect { seen += it } }
        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 10, 1)), seen)
        advanceTimeBy(9_000)
        runCurrent()
        assertEquals(1, seen.size) // 23:59:59, still the same day
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2)), seen)
        job.cancel()
    }

    @Test
    fun `a time zone change re-evaluates the date`() = runTest {
        val start = millis(ZoneOffset.UTC, 2026, 10, 1, 20, 0)
        var zone: ZoneId = ZoneOffset.UTC
        val changes = MutableSharedFlow<Unit>()
        val clock = DayClock(nowMillis = { start + testScheduler.currentTime }, zone = { zone }, externalChanges = changes)
        val seen = mutableListOf<LocalDate>()
        val job = backgroundScope.launch { clock.today.collect { seen += it } }
        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 10, 1)), seen)
        zone = ist // user lands in India
        changes.emit(Unit)
        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2)), seen)
        job.cancel()
    }
}
