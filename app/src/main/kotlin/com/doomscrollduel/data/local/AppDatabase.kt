package com.doomscrollduel.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ReelCountEntity::class, ReelHourEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reelCountDao(): ReelCountDao
    abstract fun reelHourDao(): ReelHourDao
    abstract fun reelTotalsDao(): ReelTotalsDao

    companion object {
        const val NAME = "doomscroll_duel.db"

        /** Version 2 adds hour-by-hour counts for the Progress chart. Existing day counts are untouched. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reel_hours` (`date` TEXT NOT NULL, `hour` INTEGER NOT NULL, " +
                        "`count` INTEGER NOT NULL, PRIMARY KEY(`date`, `hour`))",
                )
            }
        }
    }
}
