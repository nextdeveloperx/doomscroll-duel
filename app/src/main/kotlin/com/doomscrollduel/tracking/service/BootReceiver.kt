package com.doomscrollduel.tracking.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.doomscrollduel.blocking.BlockingScheduler
import com.doomscrollduel.domain.blocking.BlockingController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Wakes the app after a reboot, an update, or the user changing the clock or time zone.
 *
 *  - Boot or update: bring the keep-alive notification back and re-arm the wake-up for the lock and windows
 *    (WorkManager forgets nothing across reboots, but the next wake time may have moved).
 *  - Clock or time zone changed: re-check the lock and windows with the new clock, and re-arm. The lock itself
 *    does not care (it counts real elapsed time), but bedtime and focus windows are in local time and move.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var controller: BlockingController
    @Inject lateinit var scheduler: BlockingScheduler

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                TrackingKeepAlive.start(context)
                controller.status()
                scheduler.schedule()
            }
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                controller.status()
                scheduler.schedule()
            }
        }
    }
}
