package com.doomscrollduel.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Abstract class (not interface) so Room can run [increment] as one transaction — same reason, and same
 * bump-then-insert-then-bump idiom to avoid a real UPSERT, as [ReelCountDao].
 */
@Dao
abstract class ReelLifetimeDao {
    @Query("SELECT totalReels FROM reel_lifetime WHERE id = 0")
    abstract fun observeTotal(): Flow<Int?>

    @Query("UPDATE reel_lifetime SET totalReels = totalReels + 1 WHERE id = 0")
    abstract suspend fun bump(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfAbsent(entity: ReelLifetimeEntity): Long

    /** Adds one reel to the never-pruned lifetime total, creating the single row on the very first reel. Safe when
     * two reels arrive together (race-safe: a lost insert race simply falls back to a bump, as in [ReelCountDao]). */
    @Transaction
    open suspend fun increment() {
        if (bump() == 0) {
            val inserted = insertIfAbsent(ReelLifetimeEntity(0, 1))
            if (inserted == -1L) bump()
        }
    }
}
