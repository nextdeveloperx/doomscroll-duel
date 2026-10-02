package com.doomscrollduel.data.repository

import com.doomscrollduel.core.common.DayClock
import com.doomscrollduel.core.common.DayKeys
import com.doomscrollduel.data.local.ReelCountDao
import com.doomscrollduel.data.local.ReelCountEntity
import com.doomscrollduel.data.local.ReelHourDao
import com.doomscrollduel.domain.model.DailyReelStats
import com.doomscrollduel.domain.repository.ReelRepository
import com.doomscrollduel.domain.usecase.StreakCalculator
import com.doomscrollduel.tracking.model.TrackedApp
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** Room is the single source of truth for counts; the UI only ever reads from here. */
class RoomReelRepository(
    private val dao: ReelCountDao,
    private val dayClock: DayClock,
    private val zone: () -> ZoneId,
    private val nowMillis: () -> Long,
    /** Hour-by-hour counts for the Progress chart. Optional so older callers and tests need not provide it. */
    private val hourDao: ReelHourDao? = null,
    /** Per-app totals over all days, for duels. Optional so older callers and tests need not provide it. */
    private val totalsDao: com.doomscrollduel.data.local.ReelTotalsDao? = null,
) : ReelRepository {

    override suspend fun recordReel(app: TrackedApp, atMillis: Long) {
        val date = DayKeys.key(DayKeys.dateOf(atMillis, zone()))
        dao.increment(date, app.packageName, atMillis)
        hourDao?.increment(date, Instant.ofEpochMilli(atMillis).atZone(zone()).hour)
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

    override fun observeAllTimeByApp(): Flow<Map<TrackedApp, Int>> {
        val source = totalsDao ?: return flowOf(emptyMap())
        return source.observeTotalsByPackage().map { rows ->
            val totals = TrackedApp.entries.associateWith { 0 }.toMutableMap()
            rows.forEach { row -> TrackedApp.fromPackage(row.packageName)?.let { totals[it] = row.total } }
            totals
        }
    }

    override suspend fun todayTotal(): Int =
        dao.observeDay(DayKeys.key(dayClock.nowDate())).first().sumOf { it.count }

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

    override fun observeHourly(date: LocalDate): Flow<List<Int>> {
        val source = hourDao ?: return flowOf(List(24) { 0 })
        return source.observeDay(DayKeys.key(date)).map { rows ->
            val hours = IntArray(24)
            rows.forEach { if (it.hour in 0..23) hours[it.hour] = it.count }
            hours.toList()
        }
    }

    override fun observeDailyTotals(since: LocalDate): Flow<Map<LocalDate, Int>> =
        dao.observeDailyTotals(DayKeys.key(since)).map { totals -> totals.associate { DayKeys.parse(it.date) to it.total } }


    override suspend fun pruneOld() {
        val before = DayKeys.key(dayClock.nowDate().minusDays(RETENTION_DAYS))
        dao.deleteBefore(before)
        hourDao?.deleteBefore(before)
    }

    companion object {
        /** Days of history kept. A streak longer than this stops growing, which is fine. */
        const val RETENTION_DAYS = 400L
    }
}
