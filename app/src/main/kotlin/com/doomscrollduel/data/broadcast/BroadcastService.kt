package com.doomscrollduel.data.broadcast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.doomscrollduel.MainActivity
import com.doomscrollduel.R
import com.doomscrollduel.domain.social.BroadcastRepository
import com.doomscrollduel.domain.social.BroadcastStatus
import com.doomscrollduel.feature.broadcast.BroadcastBubble
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Keeps a voice room alive while the app is in the background, with a notification that says the microphone is in use.
 * It is started when the person joins a room and stopped the moment they leave; it does nothing else.
 */
@AndroidEntryPoint
class BroadcastService : Service() {
    @Inject lateinit var repo: BroadcastRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var bubble: BroadcastBubble? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val circle = BroadcastBubble(this, ::openRoom).also { bubble = it }
        // The floating circle: up while a room is live and its page is not in front (page closed, another page, or the app in the background).
        scope.launch {
            combine(repo.state, RoomScreenVisibility.visible) { state, onScreen -> state to onScreen }.collect { (state, onScreen) ->
                val shown = state.status == BroadcastStatus.LIVE && !onScreen && BubblePermission.granted(this@BroadcastService)
                if (!shown) {
                    circle.hide()
                    return@collect
                }
                val who = state.members.firstOrNull { it.speaking && !it.isMe }
                    ?: state.members.firstOrNull { it.speaking }
                    ?: state.members.firstOrNull { it.isMe }
                circle.update(who?.name.orEmpty(), who?.speaking == true)
                circle.show()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        bubble?.hide()
        bubble = null
        super.onDestroy()
    }

    private fun openRoom() {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("doomscrollduel://broadcast/room"), this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.broadcast_channel), NotificationManager.IMPORTANCE_LOW)
                .apply { description = getString(R.string.broadcast_channel_desc) },
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_brain)
            .setContentTitle(getString(R.string.broadcast_notif_title))
            .setContentText(getString(R.string.broadcast_notif_body, title))
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_CALL)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL_ID = "broadcast"
        private const val NOTIFICATION_ID = 7301
        private const val EXTRA_TITLE = "title"

        fun start(context: Context, title: String) {
            ContextCompat.startForegroundService(context, Intent(context, BroadcastService::class.java).putExtra(EXTRA_TITLE, title))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BroadcastService::class.java))
        }
    }
}
