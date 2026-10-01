package com.doomscrollduel.domain.usecase

import java.time.LocalDate

/**
 * Streak = number of days in a row the user stayed under the daily reel limit.
 *
 * - A day is kept when its total is below [limit] (reaching the limit already turns the brain to a zombie).
 * - Today counts as soon as it has data and is still under the limit; going over breaks the streak at once.
 * - A day with no row means the counter was not running, so it ends the streak. Days with zero reels
 *   do have a row (the counter creates one each day it runs), so a quiet day keeps the streak alive.
 */
object StreakCalculator {
    fun streak(today: LocalDate, totalsByDate: Map<LocalDate, Int>, limit: Int, maxDays: Int = 3_650): Int {
        var count = 0
        val todayTotal = totalsByDate[today]
        if (todayTotal != null) {
            if (todayTotal >= limit) return 0
            count++
        }
        var day = today.minusDays(1)
        repeat(maxDays) {
            val total = totalsByDate[day] ?: return count
            if (total >= limit) return count
            count++
            day = day.minusDays(1)
        }
        return count
    }
}
