package com.doomscrollduel.blocking.unlock

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Handles the friend tapping Approve or Deny on the notification. */
@AndroidEntryPoint
class UnlockActionReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: UnlockRepository

    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getStringExtra(UnlockNotifications.EXTRA_REQUEST_ID) ?: return
        val fromName = intent.getStringExtra(UnlockNotifications.EXTRA_FROM_NAME).orEmpty()
        val approve = intent.action == UnlockNotifications.ACTION_APPROVE
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val notifications = context.getSystemService(NotificationManager::class.java)
                when (repository.respond(requestId, approve)) {
                    RespondOutcome.DONE, RespondOutcome.EXPIRED, RespondOutcome.NOT_ALLOWED ->
                        notifications.cancel(UnlockNotifications.notificationId(requestId))
                    // No network or a hiccup: keep the buttons so the friend can try again.
                    RespondOutcome.NO_NETWORK, RespondOutcome.FAILED ->
                        UnlockNotifications.showRetry(context, requestId, fromName)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
