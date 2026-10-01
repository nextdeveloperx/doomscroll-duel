package com.doomscrollduel.data.repository

import com.doomscrollduel.core.common.DayClock
import com.doomscrollduel.core.common.DayKeys
import com.doomscrollduel.data.local.ReelCountDao
import com.doomscrollduel.data.local.ReelCountEntity
import com.doomscrollduel.domain.model.DailyReelStats
import com.doomscrollduel.domain.repository.ReelRepository
import com.doomscrollduel.domain.usecase.StreakCalculator
import com.doomscrollduel.tracking.model.TrackedApp
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** Room is the single source of truth for counts; the UI only ever reads from here. */
class RoomReelRepository(
    private val dao: ReelCountDao,
    private val dayClock: DayClock,
    private val zone: () -> ZoneId,
    private val nowMillis: () -> Long,
) : ReelRepository {

    override suspend fun recordReel(app: TrackedApp, atMillis: Long) {
        val date = DayKeys.key(DayKeys.dateOf(atMillis, zone()))
        dao.increment(date, app.packageName, atMillis)
    }

    override suspend fun ensureToday() {
        val date = DayKeys.key(dayClock.nowDate())
        val now = nowMillis()
        dao.insertAllIfAbsent(TrackedApp.entries.map { ReelCountEntity(date, it.packageName, 0, now) })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeToday(): Flow<DailyReelStats> =
        dayClock.today.flatMapLatest { date ->
            dao.observeDay(DayKeys.key(date)).map { rows ->
                val perApp = TrackedApp.entries.associateWith { 0 }.toMutableMap()
                var updated: Long? = null
                rows.forEach { row ->
                    TrackedApp.fromPackage(row.packageName)?.let { perApp[it] = row.count }
                    // Rows created empty at midnight do not count as "updated".
                    if (row.count > 0) updated = maxOf(updated ?: Long.MIN_VALUE, row.updatedAtMillis)
                }
                DailyReelStats(date, perApp, updated)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeStreak(limit: Flow<Int>): Flow<Int> {
        val history = dayClock.today.flatMapLatest { today ->
            val since = DayKeys.key(today.minusDays(RETENTION_DAYS))
            dao.observeDailyTotals(since).map { totals ->
                today to totals.associate { DayKeys.parse(it.date) to it.total }
            }
        }
        return combine(history, limit) { (today, totals), dailyLimit ->
            StreakCalculator.streak(today, totals, dailyLimit)
        }
    }

    override suspend fun pruneOld() {
        dao.deleteBefore(DayKeys.key(dayClock.nowDate().minusDays(RETENTION_DAYS)))
    }

    companion object {
        /** Days of history kept. A streak longer than this stops growing, which is fine. */
        const val RETENTION_DAYS = 400L
    }
}
