package com.doomscrollduel.blocking.unlock

import com.doomscrollduel.blocking.BlockingScheduler
import com.doomscrollduel.blocking.TrustedTime
import com.doomscrollduel.domain.blocking.BlockingController
import com.doomscrollduel.domain.blocking.UnlockCoordinator
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives the two pushes that make friend unlock work. Both are data messages sent at high priority, so they get
 * through Doze. A push carries ids, a first name and server times; nothing about what anyone watched.
 *
 *  - `unlock_request` (to the FRIEND): show the Approve / Deny notification.
 *  - `unlock_response` (to the person who asked): if approved, start the pass; otherwise just clear the waiting request.
 */
@AndroidEntryPoint
class UnlockMessagingService : FirebaseMessagingService() {
    @Inject lateinit var controller: BlockingController
    @Inject lateinit var scheduler: BlockingScheduler
    @Inject lateinit var trustedTime: TrustedTime
    @Inject lateinit var repository: UnlockRepository

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val requestId = data["requestId"] ?: return
        when (data["type"]) {
            "unlock_request" -> UnlockNotifications.showRequest(this, requestId, data["fromName"].orEmpty())
            "unlock_response" -> onResponse(requestId, data, message.sentTime)
        }
    }

    private fun onResponse(requestId: String, data: Map<String, String>, sentTimeMs: Long) {
        val serverSent = data["serverNowMs"]?.toLongOrNull()
        val approvedAt = data["approvedAtMs"]?.toLongOrNull()
        val approved = data["approved"] == "true"
        if (serverSent != null) {
            // The phone may have been offline for a while: add the delay so a late approval is not over-credited.
            val delay = (System.currentTimeMillis() - sentTimeMs).coerceAtLeast(0L)
            trustedTime.update(serverSent + delay)
        }
        if (approved && serverSent != null && approvedAt != null) {
            val nowOnServer = trustedTime.nowMsOrNull() ?: serverSent
            val length = UnlockCoordinator.passLengthOnArrival(approvedAt, nowOnServer)
            controller.onFriendApproved(requestId, length)
        } else {
            controller.onUnlockResolved(requestId)
        }
        scheduler.schedule()
    }

    override fun onNewToken(token: String) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { repository.registerPushToken(token) } }
    }
}
