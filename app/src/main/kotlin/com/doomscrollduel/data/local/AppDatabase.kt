package com.doomscrollduel.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ReelCountEntity::class, ReelHourEntity::class, ReelLifetimeEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reelCountDao(): ReelCountDao
    abstract fun reelHourDao(): ReelHourDao
    abstract fun reelTotalsDao(): ReelTotalsDao
    abstract fun reelLifetimeDao(): ReelLifetimeDao

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

        /**
         * Version 3 adds the never-pruned lifetime reel total that reel-milestone coin rewards are counted from
         * (see [ReelLifetimeEntity]). Seeded from the existing day-by-day history (`reel_counts`) rather than
         * starting at zero, so a long-time user's very next reward lands on their true next multiple of ten
         * instead of restarting the count — this does not retroactively grant any coins by itself, it only decides
         * where future crediting resumes from.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `reel_lifetime` (`id` INTEGER NOT NULL, `totalReels` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("INSERT INTO reel_lifetime (id, totalReels) SELECT 0, COALESCE(SUM(count), 0) FROM reel_counts")
            }
        }
    }
}
