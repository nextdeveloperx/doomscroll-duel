package com.doomscrollduel.blocking.unlock

import com.doomscrollduel.domain.challenge.PlayerId
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Talks to the Cloud Functions in `functions/src/unlock.ts`. The server is the authority for the 3 requests per
 * 24 hours, who may answer, and the push notifications; this class only maps its answers.
 *
 * NOT run against a real project yet: it needs Firebase Auth sign-in and a friends list, neither of which is built.
 */
@Singleton
class FirebaseUnlockRepository @Inject constructor() : UnlockRepository {
    private val functions get() = FirebaseFunctions.getInstance()

    override suspend fun request(buddy: PlayerId): AskResult {
        if (FirebaseAuth.getInstance().currentUser == null) return AskResult.NotSignedIn
        return try {
            val data = functions.getHttpsCallable("requestUnlock")
                .call(mapOf("buddyUid" to buddy.value))
                .await()
                .data as? Map<*, *>
            val id = data?.get("requestId") as? String
            val now = (data?.get("serverNowMs") as? Number)?.toLong()
            if (id == null || now == null) AskResult.Failed else AskResult.Sent(id, now)
        } catch (e: FirebaseFunctionsException) {
            when (e.code) {
                FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> AskResult.QuotaUsed
                FirebaseFunctionsException.Code.ALREADY_EXISTS -> AskResult.AlreadyPending
                FirebaseFunctionsException.Code.FAILED_PRECONDITION, FirebaseFunctionsException.Code.NOT_FOUND -> AskResult.NoBuddy
                FirebaseFunctionsException.Code.UNAUTHENTICATED -> AskResult.NotSignedIn
                FirebaseFunctionsException.Code.UNAVAILABLE, FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> AskResult.NoNetwork
                else -> AskResult.Failed
            }
        } catch (e: IOException) {
            AskResult.NoNetwork
        }
    }

    override suspend fun respond(requestId: String, approve: Boolean): RespondOutcome {
        if (FirebaseAuth.getInstance().currentUser == null) return RespondOutcome.NOT_ALLOWED
        return try {
            functions.getHttpsCallable("respondUnlock")
                .call(mapOf("requestId" to requestId, "approve" to approve))
                .await()
            RespondOutcome.DONE
        } catch (e: FirebaseFunctionsException) {
            when (e.code) {
                FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> RespondOutcome.EXPIRED
                FirebaseFunctionsException.Code.FAILED_PRECONDITION -> RespondOutcome.EXPIRED
                FirebaseFunctionsException.Code.PERMISSION_DENIED, FirebaseFunctionsException.Code.UNAUTHENTICATED -> RespondOutcome.NOT_ALLOWED
                FirebaseFunctionsException.Code.UNAVAILABLE -> RespondOutcome.NO_NETWORK
                else -> RespondOutcome.FAILED
            }
        } catch (e: IOException) {
            RespondOutcome.NO_NETWORK
        }
    }

    override suspend fun registerPushToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("fcmTokens").document(token)
            .set(mapOf("createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
            .await()
    }
}
