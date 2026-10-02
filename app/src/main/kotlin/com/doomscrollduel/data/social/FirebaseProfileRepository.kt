package com.doomscrollduel.data.social

import android.util.Log
import com.doomscrollduel.domain.model.Username
import com.doomscrollduel.domain.repository.AuthRepository
import com.doomscrollduel.domain.social.ClaimResult
import com.doomscrollduel.domain.social.Profile
import com.doomscrollduel.domain.social.ProfileRepository
import com.doomscrollduel.domain.social.ProfileState
import com.doomscrollduel.domain.social.UsernameAvailability
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * The signed-in person's profile, read live from `users/{uid}`.
 *
 * Choosing a username goes through the server functions (`checkUsername`, `completeProfile`). While those are not
 * deployed (or cannot be reached) the app does the same job itself against Firestore: Firestore rules let it create the
 * username and the profile together, exactly once, and nothing else (see `firebase/firestore.rules`).
 */
@Singleton
class FirebaseProfileRepository @Inject constructor(auth: AuthRepository) : ProfileRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val state: Flow<ProfileState> = auth.session
        .flatMapLatest { session -> if (session == null) flowOf(ProfileState.SignedOut) else profileOf(session.uid) }
        .distinctUntilChanged()

    private fun profileOf(uid: String): Flow<ProfileState> = callbackFlow {
        trySend(ProfileState.Loading)
        val answered = java.util.concurrent.atomic.AtomicBoolean(false)
        // No answer at all (server off, Firestore not set up, no internet): say so after a while instead of waiting forever.
        launch {
            delay(UNANSWERED_MS)
            if (!answered.get()) trySend(ProfileState.Unavailable)
        }
        val registration = FirebaseFirestore.getInstance().collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                // On an error say so; the listener retries by itself and a good answer replaces this.
                if (error != null) { trySend(ProfileState.Unavailable); return@addSnapshotListener }
                if (snapshot == null) return@addSnapshotListener
                if (!snapshot.metadata.isFromCache || snapshot.exists()) answered.set(true)
                val username = snapshot.getString("username")
                when {
                    username != null -> trySend(ProfileState.Ready(Profile(uid, username, snapshot.getString("displayName") ?: username)))
                    // Nothing saved on this phone yet and no answer from the server: do not guess "no profile".
                    snapshot.metadata.isFromCache && !snapshot.exists() -> Unit
                    else -> trySend(ProfileState.NeedsUsername)
                }
            }
        awaitClose { registration.remove() }
    }

    override suspend fun checkUsername(name: Username): UsernameAvailability =
        when (val call = callFunction("checkUsername", mapOf("username" to name.value))) {
            is CallResult.Ok -> when {
                call.data["available"] == true -> UsernameAvailability.AVAILABLE
                call.data.string("problem") == "taken" -> UsernameAvailability.TAKEN
                else -> UsernameAvailability.INVALID
            }
            CallResult.NotSignedIn -> UsernameAvailability.NOT_SIGNED_IN
            CallResult.NoNetwork -> UsernameAvailability.NO_NETWORK
            // The function is not there (yet): ask Firestore directly.
            CallResult.Failed -> checkDirect(name)
        }

    override suspend fun claimUsername(name: Username, displayName: String): ClaimResult =
        when (val call = callFunction("completeProfile", mapOf("username" to name.value, "displayName" to displayName))) {
            is CallResult.Ok -> when (call.data.string("result")) {
                "created" -> {
                    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                    ClaimResult.Created(Profile(uid, call.data.string("username") ?: name.value, call.data.string("displayName") ?: displayName))
                }
                "taken" -> ClaimResult.Taken
                "has_profile" -> ClaimResult.AlreadyHasProfile
                "invalid" -> ClaimResult.Invalid
                else -> ClaimResult.Failed
            }
            CallResult.NotSignedIn -> ClaimResult.NotSignedIn
            CallResult.NoNetwork -> ClaimResult.NoNetwork
            CallResult.Failed -> claimDirect(name, displayName)
        }

    override suspend fun updateDisplayName(name: String): Boolean {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        val clean = name.trim().take(MAX_DISPLAY_NAME)
        if (clean.isEmpty()) return false
        return try {
            FirebaseFirestore.getInstance().collection("users").document(uid).update("displayName", clean).await()
            // The People list shows this name too; a missing entry (older profile) is created by the People list itself.
            runCatching { FirebaseFirestore.getInstance().collection("directory").document(uid).update("displayName", clean).await() }
            true
        } catch (e: FirebaseFirestoreException) {
            Log.w(TAG, "display name update failed: ${e.code}", e)
            false
        } catch (e: IOException) {
            false
        }
    }

    // ----- the same two jobs without the server functions ------------------------------------------------------------

    private suspend fun checkDirect(name: Username): UsernameAvailability = try {
        val taken = FirebaseFirestore.getInstance().collection("usernames").document(name.value).get(Source.SERVER).await().exists()
        if (taken) UsernameAvailability.TAKEN else UsernameAvailability.AVAILABLE
    } catch (e: FirebaseFirestoreException) {
        Log.w(TAG, "username check failed: ${e.code}", e)
        if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) UsernameAvailability.NO_NETWORK else UsernameAvailability.FAILED
    } catch (e: IOException) {
        UsernameAvailability.NO_NETWORK
    }

    private suspend fun claimDirect(name: Username, displayName: String): ClaimResult {
        val user = FirebaseAuth.getInstance().currentUser ?: return ClaimResult.NotSignedIn
        val db = FirebaseFirestore.getInstance()
        val nameRef = db.collection("usernames").document(name.value)
        val userRef = db.collection("users").document(user.uid)
        val shown = displayName.trim().ifEmpty { user.displayName?.trim().orEmpty() }.ifEmpty { name.value }.take(MAX_DISPLAY_NAME)
        return try {
            val outcome = db.runTransaction { tx ->
                if (tx.get(nameRef).exists()) return@runTransaction OUTCOME_TAKEN
                val existing = tx.get(userRef)
                if (existing.exists() && existing.getString("username") != null) return@runTransaction OUTCOME_HAS_PROFILE
                tx.set(nameRef, mapOf("uid" to user.uid, "createdAt" to FieldValue.serverTimestamp()))
                tx.set(
                    db.collection("directory").document(user.uid),
                    mapOf("username" to name.value, "displayName" to shown, "createdAt" to FieldValue.serverTimestamp()),
                )
                tx.set(
                    userRef,
                    mapOf(
                        "username" to name.value,
                        "displayName" to shown,
                        "avatarColor" to "pink",
                        "createdAt" to FieldValue.serverTimestamp(),
                    ),
                )
                OUTCOME_CREATED
            }.await()
            when (outcome) {
                OUTCOME_TAKEN -> ClaimResult.Taken
                OUTCOME_HAS_PROFILE -> ClaimResult.AlreadyHasProfile
                else -> {
                    // Best effort: lets the server store the contact-matching hash for this profile. Ignored if it is not deployed.
                    callFunction("registerEmailHash")
                    ClaimResult.Created(Profile(user.uid, name.value, shown))
                }
            }
        } catch (e: FirebaseFirestoreException) {
            Log.w(TAG, "username claim failed: ${e.code}", e)
            when (e.code) {
                FirebaseFirestoreException.Code.UNAVAILABLE -> ClaimResult.NoNetwork
                FirebaseFirestoreException.Code.ALREADY_EXISTS -> ClaimResult.Taken
                else -> ClaimResult.Failed
            }
        } catch (e: IOException) {
            ClaimResult.NoNetwork
        }
    }

    private companion object {
        const val TAG = "ProfileRepository"
        const val OUTCOME_CREATED = 0
        const val OUTCOME_TAKEN = 1
        const val OUTCOME_HAS_PROFILE = 2
        const val MAX_DISPLAY_NAME = 30
    }
}

private const val UNANSWERED_MS = 8_000L
