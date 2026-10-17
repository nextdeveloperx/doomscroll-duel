package com.doomscrollduel.domain.social

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.IsoFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LeaderboardPeriodTest {

    // ----- week key: format and boundaries ------------------------------------------------------------------------

    @Test
    fun `week key matches the exact format already stored on live directory documents`() {
        // This format must never change without migrating every already-stored weeklyPeriodKey: a format change
        // (e.g. zero-padding the week number) would make every real person's current score look "stale" and
        // silently reset to zero on their very next read. This test exists to catch exactly that kind of change.
        listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 17), LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 1)).forEach { date ->
            val expected = "${date.get(IsoFields.WEEK_BASED_YEAR)}-W${date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"
            assertEquals(expected, LeaderboardPeriod.weekKey(date))
        }
    }

    @Test
    fun `week key is the same for every day of the ISO week and changes at the Monday boundary`() {
        val monday = LocalDate.of(2026, 10, 17).with(DayOfWeek.MONDAY)
        val keysThisWeek = (0..6).map { LeaderboardPeriod.weekKey(monday.plusDays(it.toLong())) }.toSet()
        assertEquals("every day Monday..Sunday of one ISO week must share one key", 1, keysThisWeek.size)
        assertNotEquals(LeaderboardPeriod.weekKey(monday), LeaderboardPeriod.weekKey(monday.minusDays(1)))
        assertNotEquals(LeaderboardPeriod.weekKey(monday), LeaderboardPeriod.weekKey(monday.plusDays(7)))
    }

    @Test
    fun `week key rolls over correctly across a calendar year boundary`() {
        // Dec 31 2026 is a Thursday, so it and Jan 1 2027 (Friday) fall in the SAME ISO week — the week-based year
        // in the key can legitimately differ from the plain calendar year here, by design (ISO 8601).
        val dec31 = LocalDate.of(2026, 12, 31)
        val jan1 = LocalDate.of(2027, 1, 1)
        assertEquals(DayOfWeek.THURSDAY, dec31.dayOfWeek)
        assertEquals(LeaderboardPeriod.weekKey(dec31), LeaderboardPeriod.weekKey(jan1))
    }

    // ----- month key -----------------------------------------------------------------------------------------------

    @Test
    fun `month key is the calendar year and month, zero-padded`() {
        assertEquals("2026-01", LeaderboardPeriod.monthKey(LocalDate.of(2026, 1, 15)))
        assertEquals("2026-10", LeaderboardPeriod.monthKey(LocalDate.of(2026, 10, 1)))
        assertEquals("2026-12", LeaderboardPeriod.monthKey(LocalDate.of(2026, 12, 31)))
    }

    @Test
    fun `month key is the same on every day of the month and changes at the 1st`() {
        assertEquals(LeaderboardPeriod.monthKey(LocalDate.of(2026, 10, 1)), LeaderboardPeriod.monthKey(LocalDate.of(2026, 10, 31)))
        assertNotEquals(LeaderboardPeriod.monthKey(LocalDate.of(2026, 10, 31)), LeaderboardPeriod.monthKey(LocalDate.of(2026, 11, 1)))
    }

    // ----- scoreFor: a stale period reads as zero, never carries over, never goes negative -------------------------

    @Test
    fun `current period key keeps the stored score`() {
        assertEquals(156, LeaderboardPeriod.scoreFor(156L, "2026-W42", "2026-W42"))
        assertEquals(0, LeaderboardPeriod.scoreFor(0L, "2026-W42", "2026-W42"))
    }

    @Test
    fun `an earlier period key reads as zero no matter how large the stored score was`() {
        assertEquals(0, LeaderboardPeriod.scoreFor(9_999L, "2026-W41", "2026-W42"))
    }

    @Test
    fun `a missing period key (never written) reads as zero`() {
        assertEquals(0, LeaderboardPeriod.scoreFor(50L, null, "2026-W42"))
    }

    @Test
    fun `a negative stored value never surfaces as a negative score`() {
        assertEquals(0, LeaderboardPeriod.scoreFor(-5L, "2026-W42", "2026-W42"))
    }

    // ----- sumSince: only ops at or after the period start count -----------------------------------------------

    @Test
    fun `sumSince includes an op exactly at the boundary and excludes one before it`() {
        val since = Instant.parse("2026-10-12T00:00:00Z")
        val ops = listOf(
            5 to since.minusSeconds(1), // just before the period: excluded
            7 to since, // exactly at the start: included
            3 to since.plusSeconds(60), // inside the period: included
        )
        assertEquals(10, LeaderboardPeriod.sumSince(ops, since))
    }

    @Test
    fun `sumSince of an empty op list is zero`() {
        assertEquals(0, LeaderboardPeriod.sumSince(emptyList(), Instant.EPOCH))
    }

    // ----- rankOf: the signed-in person's rank, never fabricated ----------------------------------------------

    @Test
    fun `rankOf finds my 1-based rank and entry wherever I am in the list`() {
        val entries = listOf(
            LeaderboardEntry("a", "aman", "Aman", 480, isMe = false),
            LeaderboardEntry("b", "riya", "Riya", 360, isMe = false),
            LeaderboardEntry("c", "me", "Tum", 310, isMe = true),
        )
        val (rank, entry) = requireNotNull(LeaderboardPeriod.rankOf(entries))
        assertEquals(3, rank)
        assertEquals("c", entry.uid)
    }

    @Test
    fun `rankOf at the top of the list is rank 1, not rank 0`() {
        val entries = listOf(LeaderboardEntry("me", "me", "Tum", 100, isMe = true))
        assertEquals(1, LeaderboardPeriod.rankOf(entries)?.first)
    }

    @Test
    fun `rankOf never invents a rank when I am not in the list`() {
        val entries = listOf(LeaderboardEntry("a", "aman", "Aman", 480, isMe = false))
        assertNull(LeaderboardPeriod.rankOf(entries))
    }

    @Test
    fun `rankOf on an empty list is null, not a fabricated rank`() {
        assertNull(LeaderboardPeriod.rankOf(emptyList()))
    }
}
