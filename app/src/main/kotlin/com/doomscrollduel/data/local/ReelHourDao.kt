package com.doomscrollduel.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Hour-by-hour reel counts. Same no-UPSERT pattern as [ReelCountDao], for the same old-SQLite reason. */
@Dao
abstract class ReelHourDao {
    @Query("SELECT hour, count FROM reel_hours WHERE date = :date")
    abstract fun observeDay(date: String): Flow<List<HourCount>>

    @Query("UPDATE reel_hours SET count = count + 1 WHERE date = :date AND hour = :hour")
    abstract suspend fun bump(date: String, hour: Int): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfAbsent(entity: ReelHourEntity): Long

    @Transaction
    open suspend fun increment(date: String, hour: Int) {
        if (bump(date, hour) == 0) {
            if (insertIfAbsent(ReelHourEntity(date, hour, 1)) == -1L) bump(date, hour)
        }
    }

    @Query("DELETE FROM reel_hours WHERE date < :beforeDate")
    abstract suspend fun deleteBefore(beforeDate: String): Int
}
