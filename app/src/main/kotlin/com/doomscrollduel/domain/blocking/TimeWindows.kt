package com.doomscrollduel.domain.blocking

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A time of day range such as 23:00 to 06:00. If [end] is before [start] it runs past midnight into the next
 * day. Start equal to end is not allowed (it would mean zero time or the whole day).
 */
data class TimeRange(val start: LocalTime, val end: LocalTime) {
    init {
        require(start != end) { "a range cannot start and end at the same time" }
    }

    val spansMidnight: Boolean get() = end.isBefore(start)

    val lengthMinutes: Int
        get() {
            val raw = Duration.between(start, end).toMinutes().toInt()
            return if (raw > 0) raw else raw + 24 * 60
        }

    companion object {
        /** The default bedtime: 11 PM to 6 AM. */
        val DEFAULT_BEDTIME = TimeRange(LocalTime.of(23, 0), LocalTime.of(6, 0))
    }
}

enum class WindowKind { BEDTIME, FOCUS }

/** A bedtime or focus window that is running now (or is about to), as real instants. */
data class ActiveWindow(val kind: WindowKind, val start: Instant, val end: Instant)

/**
 * Bedtime mode: one range that repeats every night (default 23:00 to 06:00).
 * [enabled] is off by default; the range is kept so turning it on again remembers it.
 */
data class BedtimeSettings(
    val enabled: Boolean = false,
    val range: TimeRange = TimeRange.DEFAULT_BEDTIME,
)

/**
 * Focus hours: a separate schedule for each weekday, each with up to [MAX_RANGES_PER_DAY] ranges. A range that
 * runs past midnight belongs to the day it STARTS on (Friday 22:00 to 02:00 is Friday's range).
 */
data class FocusSettings(
    val enabled: Boolean = false,
    val days: Map<DayOfWeek, List<TimeRange>> = emptyMap(),
) {
    fun rangesOn(day: DayOfWeek): List<TimeRange> = days[day].orEmpty()

    companion object {
        const val MAX_RANGES_PER_DAY = 3
        const val MIN_RANGE_MINUTES = 30
    }
}

/**
 * During a bedtime or focus window the reel limit becomes ZERO: every reel screen is blocked. Everything here
 * works on local wall-clock times in the CURRENT time zone, so a time-zone change simply moves the windows with
 * the user, and daylight saving nights are handled by the zone rules.
 */
object TimeWindows {

    /**
     * The window running at [now], or null. If bedtime and a focus range overlap, the one that ends LATER is
     * returned, so the screen never says "free at 7" when focus runs to 8.
     */
    fun active(bedtime: BedtimeSettings, focus: FocusSettings, now: Instant, zone: ZoneId): ActiveWindow? =
        windowsAround(bedtime, focus, now, zone, daysBefore = 1, daysAfter = 0)
            .filter { !now.isBefore(it.start) && now.isBefore(it.end) }
            .maxByOrNull { it.end }

    /**
     * The next moment, after [now], when a window starts or ends. Used to schedule the next re-check.
     * Null when nothing is scheduled in the coming week.
     */
    fun nextBoundary(bedtime: BedtimeSettings, focus: FocusSettings, now: Instant, zone: ZoneId): Instant? =
        windowsAround(bedtime, focus, now, zone, daysBefore = 1, daysAfter = 8)
            .flatMap { listOf(it.start, it.end) }
            .filter { it.isAfter(now) }
            .minOrNull()

    private fun windowsAround(
        bedtime: BedtimeSettings,
        focus: FocusSettings,
        now: Instant,
        zone: ZoneId,
        daysBefore: Long,
        daysAfter: Long,
    ): List<ActiveWindow> {
        if (!bedtime.enabled && !focus.enabled) return emptyList()
        val today = now.atZone(zone).toLocalDate()
        val result = mutableListOf<ActiveWindow>()
        for (offset in -daysBefore..daysAfter) {
            val date = today.plusDays(offset)
            if (bedtime.enabled) result += window(WindowKind.BEDTIME, date, bedtime.range, zone)
            if (focus.enabled) focus.rangesOn(date.dayOfWeek).forEach { result += window(WindowKind.FOCUS, date, it, zone) }
        }
        return result
    }

    private fun window(kind: WindowKind, date: LocalDate, range: TimeRange, zone: ZoneId): ActiveWindow {
        val start = ZonedDateTime.of(date, range.start, zone)
        val endDate = if (range.spansMidnight) date.plusDays(1) else date
        val end = ZonedDateTime.of(endDate, range.end, zone)
        return ActiveWindow(kind, start.toInstant(), end.toInstant())
    }
}

/** Moves one end of a range by a number of minutes, for the + and - buttons. A move that would make both ends equal is ignored. */
object TimeRanges {
    const val STEP_MINUTES = 30L

    fun nudgeStart(range: TimeRange, deltaMinutes: Long): TimeRange = nudge(range, deltaMinutes, moveStart = true)

    fun nudgeEnd(range: TimeRange, deltaMinutes: Long): TimeRange = nudge(range, deltaMinutes, moveStart = false)

    private fun nudge(range: TimeRange, deltaMinutes: Long, moveStart: Boolean): TimeRange {
        require(deltaMinutes != 0L) { "no movement" }
        val moved = (if (moveStart) range.start else range.end).plusMinutes(deltaMinutes)
        val fixed = if (moveStart) range.end else range.start
        if (moved == fixed) return range
        return if (moveStart) range.copy(start = moved) else range.copy(end = moved)
    }
}

/** Checks and edits for the focus schedule, so the screen can never save a nonsense schedule. */
object FocusScheduleEditor {

    enum class Problem { TOO_SHORT, TOO_MANY_RANGES, OVERLAPS }

    sealed interface Result {
        data class Ok(val focus: FocusSettings) : Result
        data class Invalid(val problem: Problem) : Result
    }

    fun add(focus: FocusSettings, day: DayOfWeek, range: TimeRange): Result {
        if (focus.rangesOn(day).size >= FocusSettings.MAX_RANGES_PER_DAY) return Result.Invalid(Problem.TOO_MANY_RANGES)
        return put(focus, day, focus.rangesOn(day) + range, changed = range)
    }

    fun replace(focus: FocusSettings, day: DayOfWeek, index: Int, range: TimeRange): Result {
        val current = focus.rangesOn(day)
        require(index in current.indices) { "no such range" }
        val others = current.filterIndexed { i, _ -> i != index }
        return put(focus, day, others + range, changed = range)
    }

    fun remove(focus: FocusSettings, day: DayOfWeek, index: Int): FocusSettings {
        val current = focus.rangesOn(day)
        require(index in current.indices) { "no such range" }
        val left = current.filterIndexed { i, _ -> i != index }
        return focus.copy(days = if (left.isEmpty()) focus.days - day else focus.days + (day to left))
    }

    private fun put(
        focus: FocusSettings,
        day: DayOfWeek,
        ranges: List<TimeRange>,
        changed: TimeRange,
    ): Result {
        if (changed.lengthMinutes < FocusSettings.MIN_RANGE_MINUTES) return Result.Invalid(Problem.TOO_SHORT)
        val candidate = focus.copy(days = focus.days + (day to ranges.sortedBy { it.start }))
        return if (overlapsAnywhere(candidate)) Result.Invalid(Problem.OVERLAPS) else Result.Ok(candidate)
    }

    /** True if any two ranges in the whole week overlap, including a range that runs past midnight into the next day. */
    fun overlapsAnywhere(focus: FocusSettings): Boolean {
        val week = 7 * 24 * 60
        val spans = focus.days.flatMap { (day, ranges) ->
            ranges.map { r ->
                val start = (day.value - 1) * 24 * 60 + r.start.hour * 60 + r.start.minute
                start to start + r.lengthMinutes
            }
        }
        for (i in spans.indices) {
            for (j in i + 1 until spans.size) {
                val (s1, e1) = spans[i]
                val (s2, e2) = spans[j]
                // Compare on a circle, because Sunday night runs into Monday morning.
                for (shift in listOf(-week, 0, week)) {
                    if (s1 < e2 + shift && s2 + shift < e1) return true
                }
            }
        }
        return false
    }
}
