package com.doomscrollduel.blocking.unlock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.doomscrollduel.MainActivity
import com.doomscrollduel.R

/**
 * The notification a friend sees when someone asks them to unlock: "Rohan ko unlock chahiye" with Approve and Deny
 * buttons that work without opening the app. It carries only the person's display name, nothing else.
 */
object UnlockNotifications {
    const val CHANNEL_ID = "unlock_requests"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.unlock_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.unlock_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun notificationId(requestId: String): Int = requestId.hashCode()

    fun showRequest(context: Context, requestId: String, fromName: String) {
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_brain)
            .setContentTitle(context.getString(R.string.unlock_request_title, fromName))
            .setContentText(context.getString(R.string.unlock_request_body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setTimeoutAfter(com.doomscrollduel.domain.blocking.UnlockRules.REQUEST_TTL.toMillis())
            .addAction(action(context, requestId, fromName, approve = true, R.string.unlock_approve))
            .addAction(action(context, requestId, fromName, approve = false, R.string.unlock_deny))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(notificationId(requestId), notification)
    }

    /** Shown when Approve or Deny could not be sent (no network): the buttons stay so the friend can try again. */
    fun showRetry(context: Context, requestId: String, fromName: String) {
        ensureChannel(context)
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_brain)
            .setContentTitle(context.getString(R.string.unlock_request_title, fromName))
            .setContentText(context.getString(R.string.unlock_retry_body))
            .setAutoCancel(true)
            .setTimeoutAfter(com.doomscrollduel.domain.blocking.UnlockRules.REQUEST_TTL.toMillis())
            .addAction(action(context, requestId, fromName, approve = true, R.string.unlock_approve))
            .addAction(action(context, requestId, fromName, approve = false, R.string.unlock_deny))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(notificationId(requestId), notification)
    }

    private fun action(context: Context, requestId: String, fromName: String, approve: Boolean, labelRes: Int): Notification.Action {
        val intent = Intent(context, UnlockActionReceiver::class.java)
            .setAction(if (approve) ACTION_APPROVE else ACTION_DENY)
            .putExtra(EXTRA_REQUEST_ID, requestId)
            .putExtra(EXTRA_FROM_NAME, fromName)
        val pending = PendingIntent.getBroadcast(
            context,
            notificationId(requestId) * 2 + if (approve) 1 else 0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(null, context.getString(labelRes), pending).build()
    }

    const val ACTION_APPROVE = "com.doomscrollduel.UNLOCK_APPROVE"
    const val ACTION_DENY = "com.doomscrollduel.UNLOCK_DENY"
    const val EXTRA_REQUEST_ID = "requestId"
    const val EXTRA_FROM_NAME = "fromName"
}
