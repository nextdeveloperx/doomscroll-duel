package com.doomscrollduel.data.social

import android.util.Log
import com.doomscrollduel.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Keeps `directory/{uid}.photoUrl` in step with the Google account photo FirebaseAuth already has after sign-in
 * (see [com.doomscrollduel.data.auth.FirebaseAuthRepository]). Every leaderboard row — Dost, India, and the
 * signed-in person's own "Your Rank" row — reads that same `directory` document (see
 * [FirebaseLeaderboardRepository]), so writing the photo URL here once is what makes the real Google profile
 * picture show up everywhere it needs to.
 *
 * Started once from [com.doomscrollduel.DuelApp], the same pattern as
 * [com.doomscrollduel.data.coins.ReelMilestoneRewarder]. Writes ONLY my own uid's document — Firestore rules
 * enforce that too — and only ever the `photoUrl` field, never username, display name or the coin totals living
 * on the same document, so this can never touch (or be touched by) anyone else's profile or score.
 */
@Singleton
class ProfilePhotoSync @Inject constructor(private val auth: AuthRepository) {

    fun start(scope: CoroutineScope) {
        scope.launch {
            auth.session.map { it?.uid }.distinctUntilChanged().collect { uid ->
                if (uid != null) sync(uid)
            }
        }
    }

    private suspend fun sync(uid: String) {
        val photoUrl = FirebaseAuth.getInstance().currentUser?.photoUrl?.toString()
        if (photoUrl.isNullOrBlank()) return // nothing to sync; rows fall back to the initials avatar
        // Right after sign-in the profile document (directory/{uid}) may not exist yet (username not chosen), or a
        // write may fail while offline — retry a few times instead of giving up for the rest of the session.
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val ref = FirebaseFirestore.getInstance().collection("directory").document(uid)
                val doc = ref.get().await()
                if (doc.exists() && doc.getString("photoUrl") != photoUrl) ref.update("photoUrl", photoUrl).await()
                return
            } catch (e: FirebaseFirestoreException) {
                Log.w(TAG, "profile photo sync failed (attempt ${attempt + 1}): ${e.code}")
            }
            delay(RETRY_DELAY_MS)
        }
    }

    private companion object {
        const val TAG = "ProfilePhotoSync"
        const val MAX_ATTEMPTS = 4
        const val RETRY_DELAY_MS = 5_000L
    }
}
