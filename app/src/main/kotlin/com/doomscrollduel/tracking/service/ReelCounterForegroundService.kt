package com.doomscrollduel.tracking.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.doomscrollduel.domain.repository.ReelRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Foreground service whose only job is to make the system treat the app as "in use", so counting is
 * not killed. It also keeps the notification's number fresh and, when the day changes, creates the
 * new day's rows and removes very old ones.
 */
@AndroidEntryPoint
class ReelCounterForegroundService : Service() {

    @Inject lateinit var repository: ReelRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        TrackingNotification.ensureChannel(this)
        startInForeground(TrackingNotification.build(this, reelsToday = 0))

        val notifications = getSystemService(NotificationManager::class.java)
        scope.launch {
            repository.ensureToday()
            repository.pruneOld()
        }
        scope.launch {
            repository.observeToday()
                .map { it.total }
                .distinctUntilChanged()
                .collect { total ->
                    runCatching {
                        notifications.notify(
                            TrackingNotification.NOTIFICATION_ID,
                            TrackingNotification.build(this@ReelCounterForegroundService, total),
                        )
                    }
                }
        }
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                TrackingNotification.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(TrackingNotification.NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

/** Starts the foreground service from anywhere, without crashing when Android says no. */
object TrackingKeepAlive {
    fun start(context: Context) {
        runCatching {
            context.startForegroundService(Intent(context, ReelCounterForegroundService::class.java))
        }
    }
}
