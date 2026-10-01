package com.doomscrollduel.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * One row per local day and app: how many reels were counted. Nothing else is stored: no content,
 * no video, no times of individual swipes.
 *
 * [date] is the local calendar day as "yyyy-MM-dd" (a new day starts a new row, which is the
 * "reset at local midnight"). [updatedAtMillis] is wall-clock time of the last change.
 */
@Entity(tableName = "reel_counts", primaryKeys = ["date", "package_name"])
data class ReelCountEntity(
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "count") val count: Int,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
)

/** Result of the per-day sum query. */
data class DayTotal(
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "total") val total: Int,
)
