package com.doomscrollduel.core.di

import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.util.Log
import androidx.room.Room
import com.doomscrollduel.core.common.DayClock
import com.doomscrollduel.data.local.AppDatabase
import com.doomscrollduel.data.local.ReelCountDao
import com.doomscrollduel.data.prefs.SharedPrefsReelLimitStore
import com.doomscrollduel.data.repository.RoomReelRepository
import com.doomscrollduel.domain.repository.ReelLimitStore
import com.doomscrollduel.domain.repository.ReelRepository
import com.doomscrollduel.tracking.detector.ReelEventProcessor
import com.doomscrollduel.tracking.detector.SurfaceRules
import com.doomscrollduel.tracking.detector.SurfaceRulesHolder
import com.doomscrollduel.tracking.detector.SurfaceRulesParser
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.ZoneId
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides
    fun reelCountDao(db: AppDatabase): ReelCountDao = db.reelCountDao()

    /** Fires when the user changes the clock or time zone, or the date rolls over. */
    @Provides
    @Singleton
    fun dayClock(@ApplicationContext context: Context): DayClock {
        val changes: Flow<Unit> = callbackFlow {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    trySend(Unit)
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            context.registerReceiver(receiver, filter)
            awaitClose { context.unregisterReceiver(receiver) }
        }
        return DayClock(
            nowMillis = System::currentTimeMillis,
            zone = { ZoneId.systemDefault() },
            externalChanges = changes,
        )
    }

    @Provides
    @Singleton
    fun reelRepository(dao: ReelCountDao, dayClock: DayClock): ReelRepository =
        RoomReelRepository(
            dao = dao,
            dayClock = dayClock,
            zone = { ZoneId.systemDefault() },
            nowMillis = System::currentTimeMillis,
        )

    /** Surface rules shipped in `assets/surface_rules.json`. If the file is broken nothing is counted, and we say so in the log. */
    @Provides
    @Singleton
    fun surfaceRulesHolder(@ApplicationContext context: Context): SurfaceRulesHolder {
        val rules = runCatching {
            val text = context.assets.open("surface_rules.json").bufferedReader().use { it.readText() }
            SurfaceRulesParser.parse(text)
        }.getOrElse {
            Log.e("SurfaceRules", "Could not load surface_rules.json; no reels will be counted", it)
            SurfaceRules.Empty
        }
        return SurfaceRulesHolder(rules)
    }

    @Provides
    @Singleton
    fun reelEventProcessor(holder: SurfaceRulesHolder): ReelEventProcessor =
        ReelEventProcessor(rules = { holder.current })
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun reelLimitStore(impl: SharedPrefsReelLimitStore): ReelLimitStore
}
