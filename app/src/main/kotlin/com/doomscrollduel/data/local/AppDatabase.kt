package com.doomscrollduel.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ReelCountEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reelCountDao(): ReelCountDao

    companion object {
        const val NAME = "doomscroll_duel.db"
    }
}
