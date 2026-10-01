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
import com.doomscrollduel.account.AccountDeletionRepository
import com.doomscrollduel.analytics.FirebaseAnalyticsSink
import com.doomscrollduel.data.prefs.DataStoreUsageDataChoice
import com.doomscrollduel.domain.analytics.Analytics
import com.doomscrollduel.domain.analytics.AnalyticsSink
import com.doomscrollduel.domain.analytics.UsageDataChoiceStore
import com.doomscrollduel.account.FirebaseAccountDeletionRepository
import com.doomscrollduel.billing.AccountProvider
import com.doomscrollduel.billing.EntitlementCache
import com.doomscrollduel.billing.EntitlementService
import com.doomscrollduel.billing.EntitlementSource
import com.doomscrollduel.billing.FirebaseAccountProvider
import com.doomscrollduel.billing.FirebaseFunctionsPurchaseVerifier
import com.doomscrollduel.billing.FirestoreEntitlementSource
import com.doomscrollduel.billing.PurchaseVerifier
import com.doomscrollduel.data.prefs.DataStoreEntitlementCache
import com.doomscrollduel.blocking.AndroidClockSource
import com.doomscrollduel.blocking.BuddyDirectory
import com.doomscrollduel.blocking.EmptyBuddyDirectory
import com.doomscrollduel.blocking.SystemZoneSource
import com.doomscrollduel.blocking.unlock.FirebaseUnlockRepository
import com.doomscrollduel.blocking.unlock.UnlockRepository
import com.doomscrollduel.data.prefs.DataStoreBlockingSettings
import com.doomscrollduel.data.prefs.DataStoreConsentStore
import com.doomscrollduel.domain.legal.ConsentStore
import com.doomscrollduel.data.prefs.DataStoreBlockingState
import com.doomscrollduel.data.prefs.SettingsBackedReelLimitStore
import com.doomscrollduel.domain.blocking.BlockingController
import com.doomscrollduel.domain.blocking.BlockingSettingsSource
import com.doomscrollduel.domain.blocking.BlockingStateStore
import com.doomscrollduel.domain.blocking.ClockSource
import com.doomscrollduel.domain.blocking.ObservableBlockingSettings
import com.doomscrollduel.domain.blocking.ZoneSource
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    abstract fun reelLimitStore(impl: SettingsBackedReelLimitStore): ReelLimitStore

    @Binds
    abstract fun observableSettings(impl: DataStoreBlockingSettings): ObservableBlockingSettings

    @Binds
    abstract fun settingsSource(impl: DataStoreBlockingSettings): BlockingSettingsSource

    @Binds
    abstract fun stateStore(impl: DataStoreBlockingState): BlockingStateStore

    @Binds
    abstract fun clockSource(impl: AndroidClockSource): ClockSource

    @Binds
    abstract fun zoneSource(impl: SystemZoneSource): ZoneSource

    @Binds
    abstract fun unlockRepository(impl: FirebaseUnlockRepository): UnlockRepository

    @Binds
    abstract fun buddyDirectory(impl: EmptyBuddyDirectory): BuddyDirectory

    @Binds
    abstract fun purchaseVerifier(impl: FirebaseFunctionsPurchaseVerifier): PurchaseVerifier

    @Binds
    abstract fun accountProvider(impl: FirebaseAccountProvider): AccountProvider

    @Binds
    abstract fun entitlementSource(impl: FirestoreEntitlementSource): EntitlementSource

    @Binds
    abstract fun entitlementCache(impl: DataStoreEntitlementCache): EntitlementCache

    @Binds
    abstract fun accountDeletion(impl: FirebaseAccountDeletionRepository): AccountDeletionRepository

    @Binds
    abstract fun analyticsSink(impl: FirebaseAnalyticsSink): AnalyticsSink

    @Binds
    abstract fun usageDataChoice(impl: DataStoreUsageDataChoice): UsageDataChoiceStore

    @Binds
    abstract fun consentStore(impl: DataStoreConsentStore): ConsentStore
}

@Module
@InstallIn(SingletonComponent::class)
object BillingModule {
    /** One entitlement service for the process, so every screen sees the same Pro answer at the same moment. */
    @Provides
    @Singleton
    fun entitlementService(source: EntitlementSource, cache: EntitlementCache): EntitlementService =
        EntitlementService(
            source = source,
            cache = cache,
            nowMs = System::currentTimeMillis,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
}

@Module
@InstallIn(SingletonComponent::class)
object BlockingModule {
    /** One controller for the whole process: the service, Settings, the worker and FCM all talk to the same one. */
    @Provides
    @Singleton
    fun blockingController(
        settings: ObservableBlockingSettings,
        store: BlockingStateStore,
        clock: ClockSource,
        zone: ZoneSource,
    ): BlockingController = BlockingController(settings, store, clock, zone)
}

@Module
@InstallIn(SingletonComponent::class)
object AnalyticsModule {
    /** The one door to analytics: events are checked for personal data and sent only when the person allowed it. */
    @Provides
    @Singleton
    fun analytics(sink: AnalyticsSink, choice: UsageDataChoiceStore): Analytics =
        Analytics(
            sink = sink,
            choice = { choice.choice.value },
            onRejected = { reason -> Log.w("Analytics", "event dropped: $reason") },
        )
}
