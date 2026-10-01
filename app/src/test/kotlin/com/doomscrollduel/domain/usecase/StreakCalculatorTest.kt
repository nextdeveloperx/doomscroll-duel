package com.doomscrollduel.domain.usecase

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakCalculatorTest {
    private val today = LocalDate.of(2026, 10, 1)
    private fun day(offset: Long) = today.minusDays(offset)

    @Test
    fun `no data is no streak`() {
        assertEquals(0, StreakCalculator.streak(today, emptyMap(), 100))
    }

    @Test
    fun `counts consecutive days under the limit including today`() {
        val totals = mapOf(day(0) to 40, day(1) to 99, day(2) to 0, day(3) to 12)
        assertEquals(4, StreakCalculator.streak(today, totals, 100))
    }

    @Test
    fun `going over the limit today breaks the streak at once`() {
        val totals = mapOf(day(0) to 100, day(1) to 10, day(2) to 10)
        assertEquals(0, StreakCalculator.streak(today, totals, 100))
    }

    @Test
    fun `an over-limit day ends the run`() {
        val totals = mapOf(day(0) to 5, day(1) to 20, day(2) to 150, day(3) to 1)
        assertEquals(2, StreakCalculator.streak(today, totals, 100))
    }

    @Test
    fun `a day with no row ends the run because the counter was off`() {
        val totals = mapOf(day(0) to 5, day(1) to 20, day(3) to 1)
        assertEquals(2, StreakCalculator.streak(today, totals, 100))
    }

    @Test
    fun `today without a row yet does not break yesterday's run`() {
        val totals = mapOf(day(1) to 20, day(2) to 30)
        assertEquals(2, StreakCalculator.streak(today, totals, 100))
    }

    @Test
    fun `a quiet day with zero reels keeps the streak`() {
        val totals = mapOf(day(0) to 0, day(1) to 0, day(2) to 0)
        assertEquals(3, StreakCalculator.streak(today, totals, 100))
    }

    @Test
    fun `the limit is the first count that breaks`() {
        assertEquals(1, StreakCalculator.streak(today, mapOf(day(0) to 99), 100))
        assertEquals(0, StreakCalculator.streak(today, mapOf(day(0) to 100), 100))
    }
}
