package com.doomscrollduel.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Everything counted so far in one app (all days kept on the phone). */
data class PackageTotal(
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "total") val total: Int,
)

/**
 * Read-only totals over `reel_counts`. A duel counts "since it started" as today's all-time total minus the all-time total
 * taken when it started, so it needs the sum across days, per app.
 */
@Dao
abstract class ReelTotalsDao {
    @Query("SELECT package_name, SUM(count) AS total FROM reel_counts GROUP BY package_name")
    abstract fun observeTotalsByPackage(): Flow<List<PackageTotal>>
}
