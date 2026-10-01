package com.doomscrollduel.data.repository

import com.doomscrollduel.core.common.DayClock
import com.doomscrollduel.core.common.DayKeys
import com.doomscrollduel.data.local.DayTotal
import com.doomscrollduel.data.local.ReelCountDao
import com.doomscrollduel.data.local.ReelCountEntity
import com.doomscrollduel.tracking.model.TrackedApp
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** In-memory stand-in for the Room DAO. Inherits the real [increment] transaction logic. */
private class FakeDao : ReelCountDao() {
    val rows = MutableStateFlow<Map<Pair<String, String>, ReelCountEntity>>(emptyMap())

    override fun observeDay(date: String): Flow<List<ReelCountEntity>> =
        rows.map { all -> all.values.filter { it.date == date } }

    override fun observeDailyTotals(sinceDate: String): Flow<List<DayTotal>> =
        rows.map { all ->
            all.values.filter { it.date >= sinceDate }.groupBy { it.date }
                .map { (date, list) -> DayTotal(date, list.sumOf { it.count }) }
        }

    override suspend fun bump(date: String, packageName: String, now: Long): Int {
        val key = date to packageName
        val row = rows.value[key] ?: return 0
        rows.value = rows.value + (key to row.copy(count = row.count + 1, updatedAtMillis = now))
        return 1
    }

    override suspend fun insertIfAbsent(entity: ReelCountEntity): Long {
        val key = entity.date to entity.packageName
        if (key in rows.value) return -1L
        rows.value = rows.value + (key to entity)
        return 1L
    }

    override suspend fun insertAllIfAbsent(entities: List<ReelCountEntity>) {
        entities.forEach { insertIfAbsent(it) }
    }

    override suspend fun deleteBefore(beforeDate: String): Int {
        val keep = rows.value.filterValues { it.date >= beforeDate }
        val removed = rows.value.size - keep.size
        rows.value = keep
        return removed
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RoomReelRepositoryTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private fun millis(y: Int, m: Int, d: Int, h: Int, min: Int = 0) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, ist).toInstant().toEpochMilli()

    private fun repo(dao: FakeDao, now: () -> Long): RoomReelRepository {
        val clock = DayClock(nowMillis = now, zone = { ist })
        return RoomReelRepository(dao, clock, zone = { ist }, nowMillis = now)
    }

    @Test
    fun `reels are counted per app and day`() = runTest {
        val dao = FakeDao()
        val now = millis(2026, 10, 1, 12)
        val repository = repo(dao) { now }
        repeat(3) { repository.recordReel(TrackedApp.INSTAGRAM, now) }
        repeat(2) { repository.recordReel(TrackedApp.YOUTUBE, now) }

        val today = repository.observeToday().first()
        assertEquals(5, today.total)
        assertEquals(3, today.perApp[TrackedApp.INSTAGRAM])
        assertEquals(2, today.perApp[TrackedApp.YOUTUBE])
        assertEquals(0, today.perApp[TrackedApp.FACEBOOK])
        assertEquals(now, today.updatedAtMillis)
    }

    @Test
    fun `a reel just after local midnight starts a new day`() = runTest {
        val dao = FakeDao()
        val repository = repo(dao) { millis(2026, 10, 2, 0, 5) }
        repository.recordReel(TrackedApp.INSTAGRAM, millis(2026, 10, 1, 23, 59))
        repository.recordReel(TrackedApp.INSTAGRAM, millis(2026, 10, 2, 0, 1))

        assertEquals(setOf("2026-10-01", "2026-10-02"), dao.rows.value.keys.map { it.first }.toSet())
        assertEquals(1, repository.observeToday().first().total) // only the 2nd is today
    }

    @Test
    fun `ensureToday adds a zero row per app and never overwrites counts`() = runTest {
        val dao = FakeDao()
        val now = millis(2026, 10, 1, 9)
        val repository = repo(dao) { now }
        repository.ensureToday()
        assertEquals(TrackedApp.entries.size, dao.rows.value.size)
        assertEquals(0, repository.observeToday().first().total)
        assertNull(repository.observeToday().first().updatedAtMillis) // empty rows are not an "update"

        repository.recordReel(TrackedApp.SNAPCHAT, now)
        repository.ensureToday()
        assertEquals(1, repository.observeToday().first().total)
    }

    @Test
    fun `the repository rolls over at midnight by itself`() = runTest {
        val dao = FakeDao()
        val start = millis(2026, 10, 1, 23, 59) + 30_000
        val now = { start + testScheduler.currentTime }
        val repository = repo(dao, now)
        repository.recordReel(TrackedApp.INSTAGRAM, now())

        val totals = mutableListOf<Int>()
        val job = backgroundScope.launch { repository.observeToday().collect { totals += it.total } }
        runCurrent()
        assertEquals(listOf(1), totals)
        advanceTimeBy(31_000)
        runCurrent()
        assertEquals(listOf(1, 0), totals) // new day, counter back to zero
        job.cancel()
    }

    @Test
    fun `streak uses the daily limit and the history in the database`() = runTest {
        val dao = FakeDao()
        val now = millis(2026, 10, 3, 10)
        val repository = repo(dao) { now }
        repeat(30) { repository.recordReel(TrackedApp.INSTAGRAM, millis(2026, 10, 1, 10)) }
        repeat(50) { repository.recordReel(TrackedApp.INSTAGRAM, millis(2026, 10, 2, 10)) }
        repeat(20) { repository.recordReel(TrackedApp.INSTAGRAM, now) }

        assertEquals(3, repository.observeStreak(flowOf(100)).first())
        assertEquals(1, repository.observeStreak(flowOf(40)).first()) // 50 on the 2nd breaks it, today (20) is under
        assertEquals(0, repository.observeStreak(flowOf(20)).first()) // today already at the limit
    }

    @Test
    fun `pruneOld deletes only rows past the retention period`() = runTest {
        val dao = FakeDao()
        val now = millis(2026, 10, 1, 10)
        val repository = repo(dao) { now }
        val old = DayKeys.dateOf(now, ist).minusDays(RoomReelRepository.RETENTION_DAYS + 1)
        dao.insertIfAbsent(ReelCountEntity(DayKeys.key(old), TrackedApp.INSTAGRAM.packageName, 9, 0))
        repository.recordReel(TrackedApp.INSTAGRAM, now)
        repository.pruneOld()
        assertEquals(listOf("2026-10-01"), dao.rows.value.keys.map { it.first })
    }

    @Test
    fun `increment inserts on the first reel and bumps afterwards`() = runTest {
        val dao = FakeDao()
        dao.increment("2026-10-01", "p", 10L)
        dao.increment("2026-10-01", "p", 20L)
        val row = dao.rows.value.getValue("2026-10-01" to "p")
        assertEquals(2, row.count)
        assertEquals(20L, row.updatedAtMillis)
    }

    @Test
    fun `a lost insert race falls back to a bump`() = runTest {
        // Another writer inserts the row between our failed bump and our insert.
        val dao = object : ReelCountDao() {
            var raced = false
            val inner = FakeDao()
            override fun observeDay(date: String) = inner.observeDay(date)
            override fun observeDailyTotals(sinceDate: String) = inner.observeDailyTotals(sinceDate)
            override suspend fun bump(date: String, packageName: String, now: Long): Int {
                val result = inner.bump(date, packageName, now)
                if (result == 0 && !raced) {
                    raced = true
                    inner.insertIfAbsent(ReelCountEntity(date, packageName, 5, 1L))
                }
                return result
            }
            override suspend fun insertIfAbsent(entity: ReelCountEntity) = inner.insertIfAbsent(entity)
            override suspend fun insertAllIfAbsent(entities: List<ReelCountEntity>) = inner.insertAllIfAbsent(entities)
            override suspend fun deleteBefore(beforeDate: String) = inner.deleteBefore(beforeDate)
        }
        dao.increment("2026-10-01", "p", 99L)
        assertEquals(6, dao.inner.rows.value.getValue("2026-10-01" to "p").count)
    }
}
