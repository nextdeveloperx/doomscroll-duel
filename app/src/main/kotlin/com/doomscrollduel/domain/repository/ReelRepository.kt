package com.doomscrollduel.domain.repository

import com.doomscrollduel.domain.model.DailyReelStats
import com.doomscrollduel.tracking.model.TrackedApp
import kotlinx.coroutines.flow.Flow

interface ReelRepository {
    /** Adds one reel for [app] on the local day that contains [atMillis] (wall-clock time). */
    suspend fun recordReel(app: TrackedApp, atMillis: Long)

    /** Makes sure today has a row for every app (zero reels), so a quiet day still counts as tracked. */
    suspend fun ensureToday()

    /** Today's counts. Switches to the new day at local midnight by itself. */
    fun observeToday(): Flow<DailyReelStats>

    /** Days in a row under the daily limit, recomputed when counts or [limit] change. */
    fun observeStreak(limit: Flow<Int>): Flow<Int>

    /** Deletes rows older than the retention period. */
    suspend fun pruneOld()
}

interface ReelLimitStore {
    /** Daily reel limit used for the brain and the streak. */
    val limit: Flow<Int>

    suspend fun setLimit(limit: Int)

    companion object {
        const val DEFAULT = 100
        const val MIN = 10
        const val MAX = 1_000
    }
}
