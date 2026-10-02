package com.doomscrollduel.data.social

import android.util.Log
import com.doomscrollduel.domain.repository.AuthRepository
import com.doomscrollduel.domain.repository.Friend
import com.doomscrollduel.domain.social.ChallengeOutcome
import com.doomscrollduel.domain.social.DuelAction
import com.doomscrollduel.domain.social.DuelInfo
import com.doomscrollduel.domain.social.DuelRepository
import com.doomscrollduel.domain.social.DuelStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

/** See [DuelRepository]. Documents: `duels/{id}` and `duels/{id}/counts/{uid}`; the rules in firestore.rules decide who may write what. */
@Singleton
class FirebaseDuelRepository @Inject constructor(auth: AuthRepository) : DuelRepository {

    private val db get() = FirebaseFirestore.getInstance()

    @OptIn(ExperimentalCoroutinesApi::class)
    override val duels: Flow<List<DuelInfo>> = auth.session.flatMapLatest { session ->
        if (session == null) flowOf(emptyList()) else duelsOf(session.uid)
    }

    private fun duelsOf(me: String): Flow<List<DuelInfo>> = callbackFlow {
        val registration = db.collection("duels").whereArrayContains("players", me).addSnapshotListener { snapshot, error ->
            if (error != null) { Log.w(TAG, "duels listener: ${error.code}"); return@addSnapshotListener }
            if (snapshot != null) trySend(snapshot.documents.mapNotNull { it.toDuel() }.sortedByDescending { it.createdAtMs })
        }
        awaitClose { registration.remove() }
    }

    override fun duel(id: String): Flow<DuelInfo?> = callbackFlow {
        val registration = db.collection("duels").document(id).addSnapshotListener { snapshot, error ->
            if (error != null) { Log.w(TAG, "duel listener: ${error.code}"); trySend(null); return@addSnapshotListener }
            if (snapshot != null) trySend(snapshot.takeIf { it.exists() }?.toDuel())
        }
        awaitClose { registration.remove() }
    }

    override fun counts(id: String): Flow<Map<String, Int>> = callbackFlow {
        val registration = db.collection("duels").document(id).collection("counts").addSnapshotListener { snapshot, error ->
            // Before the duel starts (or if the rules refuse) there is nothing to read; that is not an error to show.
            if (error != null) { trySend(emptyMap()); return@addSnapshotListener }
            if (snapshot != null) trySend(snapshot.documents.associate { it.id to (it.getLong("total") ?: 0L).toInt() })
        }
        awaitClose { registration.remove() }
    }

    private fun DocumentSnapshot.toDuel(): DuelInfo? {
        val challenger = getString("challengerUid") ?: return null
        val opponent = getString("opponentUid") ?: return null
        val status = when (getString("status")) {
            "pending" -> DuelStatus.PENDING
            "active" -> DuelStatus.ACTIVE
            "finished" -> DuelStatus.FINISHED
            "declined" -> DuelStatus.DECLINED
            "cancelled" -> DuelStatus.CANCELLED
            else -> return null
        }
        return DuelInfo(
            id = id,
            challengerUid = challenger,
            opponentUid = opponent,
            challengerName = getString("challengerName") ?: "?",
            opponentName = getString("opponentName") ?: "?",
            status = status,
            reelLimit = (getLong("reelLimit") ?: 100L).toInt(),
            hours = (getLong("hours") ?: 24L).toInt(),
            stakeCoins = (getLong("stakeCoins") ?: 50L).toInt(),
            // A challenge just written has no server time yet; "now" keeps it in order until the server answers.
            createdAtMs = getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis(),
            startAtMs = getTimestamp("startAt")?.toDate()?.time,
        )
    }

    override suspend fun challenge(friend: Friend, reelLimit: Int, hours: Int, stakeCoins: Int): ChallengeOutcome {
        val me = FirebaseAuth.getInstance().currentUser?.uid ?: return ChallengeOutcome(DuelAction.NOT_SIGNED_IN)
        return try {
            val mine = db.collection("users").document(me).get().await()
            val myName = (mine.getString("displayName") ?: mine.getString("username"))?.take(MAX_NAME) ?: return ChallengeOutcome(DuelAction.FAILED)
            val ref = db.collection("duels").document()
            ref.set(
                mapOf(
                    "players" to listOf(me, friend.uid),
                    "challengerUid" to me,
                    "opponentUid" to friend.uid,
                    "challengerName" to myName,
                    "opponentName" to friend.displayName.take(MAX_NAME),
                    "status" to "pending",
                    "reelLimit" to reelLimit,
                    "hours" to hours,
                    "stakeCoins" to stakeCoins,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            ChallengeOutcome(DuelAction.OK, ref.id)
        } catch (e: FirebaseFirestoreException) {
            Log.w(TAG, "challenge failed: ${e.code}", e)
            ChallengeOutcome(
                when (e.code) {
                    Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED -> DuelAction.NO_NETWORK
                    Code.PERMISSION_DENIED -> DuelAction.NOT_FRIENDS
                    else -> DuelAction.FAILED
                },
            )
        } catch (e: IOException) {
            ChallengeOutcome(DuelAction.NO_NETWORK)
        }
    }

    override suspend fun accept(id: String): DuelAction =
        update(id, mapOf("status" to "active", "startAt" to FieldValue.serverTimestamp()))

    override suspend fun decline(id: String): DuelAction = update(id, mapOf("status" to "declined"))

    override suspend fun cancel(id: String): DuelAction = update(id, mapOf("status" to "cancelled"))

    override suspend fun finish(id: String): DuelAction = update(id, mapOf("status" to "finished"))

    private suspend fun update(id: String, fields: Map<String, Any>): DuelAction = try {
        db.collection("duels").document(id).update(fields).await()
        DuelAction.OK
    } catch (e: FirebaseFirestoreException) {
        Log.w(TAG, "duel update failed: ${e.code}", e)
        when (e.code) {
            Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED -> DuelAction.NO_NETWORK
            // The other player (or the clock) already changed it, so this move is no longer allowed.
            Code.PERMISSION_DENIED, Code.NOT_FOUND, Code.FAILED_PRECONDITION -> DuelAction.GONE
            else -> DuelAction.FAILED
        }
    } catch (e: IOException) {
        DuelAction.NO_NETWORK
    }

    override suspend fun uploadCount(id: String, perApp: Map<String, Int>): DuelAction {
        val me = FirebaseAuth.getInstance().currentUser?.uid ?: return DuelAction.NOT_SIGNED_IN
        val apps = listOf("instagram", "youtube", "facebook", "snapchat").associateWith { (perApp[it] ?: 0).coerceAtLeast(0) }
        return try {
            db.collection("duels").document(id).collection("counts").document(me).set(
                mapOf(
                    "uid" to me,
                    "total" to apps.values.sum(),
                    "perApp" to apps,
                    "dateKey" to LocalDate.now().toString(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            DuelAction.OK
        } catch (e: FirebaseFirestoreException) {
            Log.w(TAG, "count upload failed: ${e.code}")
            when (e.code) {
                Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED -> DuelAction.NO_NETWORK
                Code.PERMISSION_DENIED -> DuelAction.GONE
                else -> DuelAction.FAILED
            }
        } catch (e: IOException) {
            DuelAction.NO_NETWORK
        }
    }

    private companion object {
        const val TAG = "DuelRepository"
        const val MAX_NAME = 30
    }
}
