package com.doomscrollduel.blocking

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.doomscrollduel.domain.blocking.BlockingController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wakes the app at the next moment something changes: the lock ends, a pass ends, a bedtime or focus window starts or
 * ends, a friend request expires, or midnight.
 *
 * WorkManager is only HOUSEKEEPING here. It may run late (Doze, battery savers, a killed app) and that is fine,
 * because every block decision is made from timestamps at the moment a reel screen is seen, never from a timer
 * that must fire on time. What the worker does: bring the saved state up to date (so a finished lock is cleared
 * and written), and schedule the next wake. It needs no network.
 */
@Singleton
class BlockingScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val controller: BlockingController,
) {
    /** Replaces any earlier wake with one at the next boundary. Cheap; call after anything that changes a timer. */
    fun schedule() {
        val delayMs = controller.nextWakeDelayMs() ?: return
        val request = OneTimeWorkRequestBuilder<BlockingWakeWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val UNIQUE_NAME = "blocking-wake"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BlockingWorkerEntryPoint {
    fun controller(): BlockingController

    fun scheduler(): BlockingScheduler
}

class BlockingWakeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(applicationContext, BlockingWorkerEntryPoint::class.java)
        entry.controller().status() // brings the lock, pass and requests up to date and saves what ended
        entry.scheduler().schedule()
        return Result.success()
    }
}
