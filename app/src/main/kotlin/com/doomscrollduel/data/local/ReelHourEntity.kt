package com.doomscrollduel.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * One row per local day and clock hour: how many reels were counted in that hour, all apps together.
 * Only a number per hour is kept (for the Progress chart); never what was watched or the time of single swipes.
 */
@Entity(tableName = "reel_hours", primaryKeys = ["date", "hour"])
data class ReelHourEntity(
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "hour") val hour: Int,
    @ColumnInfo(name = "count") val count: Int,
)

/** One hour of one day. */
data class HourCount(
    @ColumnInfo(name = "hour") val hour: Int,
    @ColumnInfo(name = "count") val count: Int,
)
