package com.doomscrollduel.feature.friends

import com.doomscrollduel.blocking.unlock.UnlockRepository
import com.doomscrollduel.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Saves this phone's push address under the signed-in person, so friends' invites and unlock requests reach it, and takes
 * it away again on logout. Runs each time someone signs in (and at every app start while signed in); a new token later is
 * handled by the messaging service. Failures are ignored: the next start tries again.
 */
@Singleton
class PushTokenRegistrar @Inject constructor(
    private val auth: AuthRepository,
    private val unlock: UnlockRepository,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            auth.session.collect { session ->
                if (session != null) {
                    runCatching { unlock.registerPushToken(FirebaseMessaging.getInstance().token.await()) }
                }
            }
        }
    }

    /**
     * Logout: forget this phone under the account (so the next person on it gets nothing meant for this one) and drop the
     * local token. Gives up after a few seconds when offline, so logging out never hangs.
     */
    suspend fun unregister() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        withTimeoutOrNull(TIMEOUT_MS) {
            runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                FirebaseFirestore.getInstance().collection("users").document(uid).collection("fcmTokens").document(token).delete().await()
            }
        }
        runCatching { withTimeoutOrNull(TIMEOUT_MS) { FirebaseMessaging.getInstance().deleteToken().await() } }
    }

    private companion object {
        const val TIMEOUT_MS = 3_000L
    }
}
