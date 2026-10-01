package com.doomscrollduel.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Abstract class (not interface) so Room can run [increment] as one transaction.
 * Written without SQLite UPSERT, which needs SQLite 3.24 and our minimum Android 8.0 ships 3.18.
 */
@Dao
abstract class ReelCountDao {
    @Query("SELECT * FROM reel_counts WHERE date = :date")
    abstract fun observeDay(date: String): Flow<List<ReelCountEntity>>

    @Query("SELECT date, SUM(count) AS total FROM reel_counts WHERE date >= :sinceDate GROUP BY date")
    abstract fun observeDailyTotals(sinceDate: String): Flow<List<DayTotal>>

    @Query(
        "UPDATE reel_counts SET count = count + 1, updated_at = :now " +
            "WHERE date = :date AND package_name = :packageName",
    )
    abstract suspend fun bump(date: String, packageName: String, now: Long): Int

    /** Returns the new row id, or -1 when the row already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfAbsent(entity: ReelCountEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAllIfAbsent(entities: List<ReelCountEntity>)

    /** Adds one reel, creating the day's row on the first one. Safe when two reels arrive together. */
    @Transaction
    open suspend fun increment(date: String, packageName: String, now: Long) {
        if (bump(date, packageName, now) == 0) {
            val inserted = insertIfAbsent(ReelCountEntity(date, packageName, 1, now))
            if (inserted == -1L) bump(date, packageName, now)
        }
    }

    @Query("DELETE FROM reel_counts WHERE date < :beforeDate")
    abstract suspend fun deleteBefore(beforeDate: String): Int
}
