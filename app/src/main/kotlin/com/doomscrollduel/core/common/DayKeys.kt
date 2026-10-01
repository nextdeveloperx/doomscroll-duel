package com.doomscrollduel.core.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Local-calendar-day helpers. Day boundaries follow the phone's current time zone. */
object DayKeys {
    fun dateOf(epochMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    /** "2026-10-01". Sorts the same as the dates do, which the Room queries rely on. */
    fun key(date: LocalDate): String = date.toString()

    fun parse(key: String): LocalDate = LocalDate.parse(key)

    /** Milliseconds from [nowMillis] to the next local midnight, at least 1. Handles 23 and 25 hour days. */
    fun millisUntilNextDay(nowMillis: Long, zone: ZoneId): Long {
        val nextMidnight = dateOf(nowMillis, zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return (nextMidnight - nowMillis).coerceAtLeast(1L)
    }
}
