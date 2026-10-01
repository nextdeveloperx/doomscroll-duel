package com.doomscrollduel.domain.model

import com.doomscrollduel.tracking.model.TrackedApp
import java.time.LocalDate

/** Today's counts. [perApp] always has an entry for every tracked app, zero when unused. */
data class DailyReelStats(
    val date: LocalDate,
    val perApp: Map<TrackedApp, Int>,
    val updatedAtMillis: Long?,
) {
    val total: Int get() = perApp.values.sum()

    companion object {
        fun empty(date: LocalDate) = DailyReelStats(date, TrackedApp.entries.associateWith { 0 }, null)
    }
}
