package com.doomscrollduel.domain.blocking

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeWindowsTest {
    private val bedtimeOn = BedtimeSettings(enabled = true)
    private val off = BedtimeSettings(enabled = false)
    private val noFocus = FocusSettings()

    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)
    private fun active(b: BedtimeSettings, f: FocusSettings, ms: Long, zone: ZoneId = IST) = TimeWindows.active(b, f, inst(ms), zone)

    // ----- bedtime ---------------------------------------------------------------------------------

    @Test
    fun `the default bedtime is 11 PM to 6 AM`() {
        assertEquals(TimeRange(t(23), t(6)), TimeRange.DEFAULT_BEDTIME)
        assertTrue(TimeRange.DEFAULT_BEDTIME.spansMidnight)
        assertEquals(7 * 60, TimeRange.DEFAULT_BEDTIME.lengthMinutes)
    }

    @Test
    fun `bedtime is off until enabled`() {
        assertNull(active(off, noFocus, wall(IST, 2026, 10, 1, 23, 30)))
    }

    @Test
    fun `bedtime boundaries 23 00 is in and 06 00 is out`() {
        assertNull(active(bedtimeOn, noFocus, wall(IST, 2026, 10, 1, 22, 59, 59)))
        assertEquals(WindowKind.BEDTIME, active(bedtimeOn, noFocus, wall(IST, 2026, 10, 1, 23, 0))!!.kind)
        assertEquals(WindowKind.BEDTIME, active(bedtimeOn, noFocus, wall(IST, 2026, 10, 2, 0, 0))!!.kind)
        assertEquals(WindowKind.BEDTIME, active(bedtimeOn, noFocus, wall(IST, 2026, 10, 2, 5, 59, 59))!!.kind)
        assertNull(active(bedtimeOn, noFocus, wall(IST, 2026, 10, 2, 6, 0)))
    }

    @Test
    fun `the window carries its real start and end`() {
        val w = active(bedtimeOn, noFocus, wall(IST, 2026, 10, 2, 1, 0))!!
        assertEquals(inst(wall(IST, 2026, 10, 1, 23, 0)), w.start)
        assertEquals(inst(wall(IST, 2026, 10, 2, 6, 0)), w.end)
    }

    @Test
    fun `a same day range does not span midnight`() {
        val daytime = BedtimeSettings(true, TimeRange(t(13), t(15)))
        assertFalse(daytime.range.spansMidnight)
        assertNull(active(daytime, noFocus, wall(IST, 2026, 10, 1, 12, 59)))
        assertEquals(WindowKind.BEDTIME, active(daytime, noFocus, wall(IST, 2026, 10, 1, 14, 0))!!.kind)
        assertNull(active(daytime, noFocus, wall(IST, 2026, 10, 1, 15, 0)))
    }

    @Test
    fun `an equal start and end is rejected`() {
        try {
            TimeRange(t(9), t(9))
            throw AssertionError("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    // ----- time zones and daylight saving --------------------------------------------------------------

    @Test
    fun `windows follow the current time zone`() {
        val utc = ZoneId.of("UTC")
        val instantAt2300Utc = wall(utc, 2026, 10, 1, 23, 30)
        assertEquals(WindowKind.BEDTIME, active(bedtimeOn, noFocus, instantAt2300Utc, utc)!!.kind)
        // The same instant is 05:00 the next morning in India: still inside 23:00 to 06:00.
        assertEquals(WindowKind.BEDTIME, active(bedtimeOn, noFocus, instantAt2300Utc, IST)!!.kind)
        // But 18:00 UTC is 23:30 IST (bedtime) and only 18:00 UTC (daytime).
        val at1800Utc = wall(utc, 2026, 10, 1, 18, 0)
        assertEquals(WindowKind.BEDTIME, active(bedtimeOn, noFocus, at1800Utc, IST)!!.kind)
        assertNull(active(bedtimeOn, noFocus, at1800Utc, utc))
    }

    @Test
    fun `travelling east moves the window with the user`() {
        val instant = wall(IST, 2026, 10, 1, 21, 0) // 21:00 in India, daytime
        assertNull(active(bedtimeOn, noFocus, instant, IST))
        assertEquals(WindowKind.BEDTIME, active(bedtimeOn, noFocus, instant, ZoneId.of("Asia/Tokyo"))!!.kind) // 00:30 in Tokyo
    }

    @Test
    fun `a daylight saving night is handled by the zone`() {
        val ny = ZoneId.of("America/New_York")
        // 8 March 2026: clocks jump from 02:00 to 03:00. 04:00 local is still bedtime, 06:00 ends it.
        assertEquals(WindowKind.BEDTIME, active(bedtimeOn, noFocus, wall(ny, 2026, 3, 8, 4, 0), ny)!!.kind)
        assertNull(active(bedtimeOn, noFocus, wall(ny, 2026, 3, 8, 6, 0), ny))
        // 1 November 2026: clocks fall back, 01:30 happens twice and both are bedtime.
        val first = wall(ny, 2026, 11, 1, 1, 30)
        assertEquals(WindowKind.BEDTIME, active(bedtimeOn, noFocus, first, ny)!!.kind)
        assertEquals(WindowKind.BEDTIME, active(bedtimeOn, noFocus, first + hoursMs(1), ny)!!.kind)
    }

    // ----- focus hours -----------------------------------------------------------------------------------

    private val studyMonToFri = FocusSettings(
        enabled = true,
        days = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
            .associateWith { listOf(TimeRange(t(16), t(19))) },
    )

    @Test
    fun `focus hours apply only on the days they are set`() {
        // 1 Oct 2026 is a Thursday, 3 Oct a Saturday.
        assertEquals(WindowKind.FOCUS, active(off, studyMonToFri, wall(IST, 2026, 10, 1, 17, 0))!!.kind)
        assertNull(active(off, studyMonToFri, wall(IST, 2026, 10, 3, 17, 0)))
        assertNull(active(off, studyMonToFri, wall(IST, 2026, 10, 1, 19, 0)))
    }

    @Test
    fun `a focus range past midnight belongs to the day it starts`() {
        val friNight = FocusSettings(true, mapOf(DayOfWeek.FRIDAY to listOf(TimeRange(t(22), t(2)))))
        // Saturday 01:00 is Friday's range (2 Oct 2026 is a Friday, so 3 Oct 01:00 is inside).
        assertEquals(WindowKind.FOCUS, active(off, friNight, wall(IST, 2026, 10, 3, 1, 0))!!.kind)
        // Saturday 23:00 is not: Saturday has no range.
        assertNull(active(off, friNight, wall(IST, 2026, 10, 3, 23, 0)))
    }

    @Test
    fun `several ranges in one day`() {
        val twoRanges = FocusSettings(true, mapOf(DayOfWeek.THURSDAY to listOf(TimeRange(t(9), t(11)), TimeRange(t(16), t(18)))))
        assertEquals(WindowKind.FOCUS, active(off, twoRanges, wall(IST, 2026, 10, 1, 10, 0))!!.kind)
        assertNull(active(off, twoRanges, wall(IST, 2026, 10, 1, 13, 0)))
        assertEquals(WindowKind.FOCUS, active(off, twoRanges, wall(IST, 2026, 10, 1, 17, 0))!!.kind)
    }

    @Test
    fun `focus is off when not enabled even with a schedule`() {
        assertNull(active(off, studyMonToFri.copy(enabled = false), wall(IST, 2026, 10, 1, 17, 0)))
    }

    @Test
    fun `overlapping bedtime and focus report the one that ends later`() {
        val lateStudy = FocusSettings(true, mapOf(DayOfWeek.THURSDAY to listOf(TimeRange(t(22), t(23, 59)))))
        val w = active(bedtimeOn, lateStudy, wall(IST, 2026, 10, 1, 23, 30))!!
        assertEquals(WindowKind.BEDTIME, w.kind) // bedtime runs to 06:00, focus to 23:59
        assertEquals(inst(wall(IST, 2026, 10, 2, 6, 0)), w.end)
    }

    // ----- next boundary ------------------------------------------------------------------------------------

    @Test
    fun `the next boundary is the next start or end`() {
        val now = wall(IST, 2026, 10, 1, 12, 0)
        assertEquals(inst(wall(IST, 2026, 10, 1, 23, 0)), TimeWindows.nextBoundary(bedtimeOn, noFocus, inst(now), IST))
        val midWindow = wall(IST, 2026, 10, 2, 1, 0)
        assertEquals(inst(wall(IST, 2026, 10, 2, 6, 0)), TimeWindows.nextBoundary(bedtimeOn, noFocus, inst(midWindow), IST))
    }

    @Test
    fun `no boundary when nothing is enabled`() {
        assertNull(TimeWindows.nextBoundary(off, noFocus, inst(wall(IST, 2026, 10, 1, 12, 0)), IST))
    }

    @Test
    fun `focus boundaries are found days ahead`() {
        // Saturday noon: next focus start is Monday 16:00.
        val sat = wall(IST, 2026, 10, 3, 12, 0)
        assertEquals(inst(wall(IST, 2026, 10, 5, 16, 0)), TimeWindows.nextBoundary(off, studyMonToFri, inst(sat), IST))
    }
}

class FocusScheduleEditorTest {
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)
    private val empty = FocusSettings(enabled = true)

    private fun ok(r: FocusScheduleEditor.Result) = (r as FocusScheduleEditor.Result.Ok).focus
    private fun problem(r: FocusScheduleEditor.Result) = (r as FocusScheduleEditor.Result.Invalid).problem

    @Test
    fun `adding a range works and keeps them sorted`() {
        val a = ok(FocusScheduleEditor.add(empty, DayOfWeek.MONDAY, TimeRange(t(16), t(18))))
        val b = ok(FocusScheduleEditor.add(a, DayOfWeek.MONDAY, TimeRange(t(9), t(10))))
        assertEquals(listOf(t(9), t(16)), b.rangesOn(DayOfWeek.MONDAY).map { it.start })
    }

    @Test
    fun `a range must be at least 30 minutes`() {
        assertEquals(FocusScheduleEditor.Problem.TOO_SHORT, problem(FocusScheduleEditor.add(empty, DayOfWeek.MONDAY, TimeRange(t(9), t(9, 29)))))
        ok(FocusScheduleEditor.add(empty, DayOfWeek.MONDAY, TimeRange(t(9), t(9, 30))))
    }

    @Test
    fun `at most three ranges a day`() {
        var f = empty
        for (h in listOf(6, 10, 14)) f = ok(FocusScheduleEditor.add(f, DayOfWeek.MONDAY, TimeRange(t(h), t(h + 1))))
        assertEquals(FocusScheduleEditor.Problem.TOO_MANY_RANGES, problem(FocusScheduleEditor.add(f, DayOfWeek.MONDAY, TimeRange(t(20), t(21)))))
    }

    @Test
    fun `overlapping ranges are refused, touching ones are fine`() {
        val a = ok(FocusScheduleEditor.add(empty, DayOfWeek.MONDAY, TimeRange(t(9), t(11))))
        assertEquals(FocusScheduleEditor.Problem.OVERLAPS, problem(FocusScheduleEditor.add(a, DayOfWeek.MONDAY, TimeRange(t(10), t(12)))))
        ok(FocusScheduleEditor.add(a, DayOfWeek.MONDAY, TimeRange(t(11), t(12))))
    }

    @Test
    fun `a range past midnight cannot overlap the next day`() {
        val monNight = ok(FocusScheduleEditor.add(empty, DayOfWeek.MONDAY, TimeRange(t(22), t(2))))
        assertEquals(FocusScheduleEditor.Problem.OVERLAPS, problem(FocusScheduleEditor.add(monNight, DayOfWeek.TUESDAY, TimeRange(t(1), t(3)))))
        ok(FocusScheduleEditor.add(monNight, DayOfWeek.TUESDAY, TimeRange(t(2), t(3))))
    }

    @Test
    fun `sunday night wraps into monday`() {
        val sunNight = ok(FocusScheduleEditor.add(empty, DayOfWeek.SUNDAY, TimeRange(t(23), t(3))))
        assertEquals(FocusScheduleEditor.Problem.OVERLAPS, problem(FocusScheduleEditor.add(sunNight, DayOfWeek.MONDAY, TimeRange(t(0), t(1)))))
    }

    @Test
    fun `replace and remove`() {
        val a = ok(FocusScheduleEditor.add(empty, DayOfWeek.MONDAY, TimeRange(t(9), t(11))))
        val moved = ok(FocusScheduleEditor.replace(a, DayOfWeek.MONDAY, 0, TimeRange(t(9, 30), t(11)))) // overlaps only itself
        assertEquals(t(9, 30), moved.rangesOn(DayOfWeek.MONDAY).single().start)
        val none = FocusScheduleEditor.remove(moved, DayOfWeek.MONDAY, 0)
        assertTrue(none.days.isEmpty())
    }
}

class TimeRangesAndFormatTest {
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

    @Test
    fun `nudging moves one end by half an hour and wraps around midnight`() {
        val bed = TimeRange(t(23), t(6))
        assertEquals(TimeRange(t(23, 30), t(6)), TimeRanges.nudgeStart(bed, 30))
        assertEquals(TimeRange(t(0, 30), t(6)), TimeRanges.nudgeStart(TimeRange(t(23, 30), t(6)), 60))
        assertEquals(TimeRange(t(23), t(5, 30)), TimeRanges.nudgeEnd(bed, -30))
        assertEquals(TimeRange(t(22, 30), t(6)), TimeRanges.nudgeStart(bed, -30))
    }

    @Test
    fun `a move that would make both ends equal is ignored`() {
        val range = TimeRange(t(5, 30), t(6))
        assertEquals(range, TimeRanges.nudgeStart(range, 30)) // would land on 6:00
        assertEquals(range, TimeRanges.nudgeEnd(range, -30)) // would land on 5:30
        assertEquals(TimeRange(t(5), t(6)), TimeRanges.nudgeStart(range, -30))
    }

    @Test
    fun `twelve hour labels`() {
        assertEquals("11:00 PM", TimeFormat.time12(t(23)))
        assertEquals("6:30 AM", TimeFormat.time12(t(6, 30)))
        assertEquals("12:00 AM", TimeFormat.time12(t(0)))
        assertEquals("12:15 PM", TimeFormat.time12(t(12, 15)))
        assertEquals("1:05 PM", TimeFormat.time12(t(13, 5)))
    }

    @Test
    fun `countdown text rounds up and never shows zero early`() {
        assertEquals("00:01", TimeFormat.clock(1))
        assertEquals("00:01", TimeFormat.clock(1_000))
        assertEquals("00:02", TimeFormat.clock(1_001))
        assertEquals("00:00", TimeFormat.clock(0))
        assertEquals("00:00", TimeFormat.clock(-5))
        assertEquals("10:00", TimeFormat.clock(600_000))
        assertEquals("1:23:45", TimeFormat.clock(hoursMs(1) + minutesMs(23) + 45_000))
        assertEquals("10:00:00", TimeFormat.clock(hoursMs(10)))
    }

    @Test
    fun `time left in words rounds up to the minute`() {
        assertEquals("1 min", TimeFormat.words(1))
        assertEquals("5 min", TimeFormat.words(minutesMs(5)))
        assertEquals("1 ghante", TimeFormat.words(hoursMs(1)))
        assertEquals("2 ghante 1 min", TimeFormat.words(hoursMs(2) + 1))
        assertEquals("0 min", TimeFormat.words(0))
    }
}
