package com.doomscrollduel.billing

import com.doomscrollduel.domain.billing.Entitlement
import com.doomscrollduel.domain.billing.EntitlementCodec
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Listens to `entitlements/{uid}`, a document only Cloud Functions can write (see firestore.rules). Follows sign-in
 * and sign-out: a new account gets its own listener.
 *
 * Emits null ONLY when the answer is certain: nobody is signed in, or the server itself says the document does not
 * exist. A "does not exist" that came from the local cache while offline is ignored, because it would wipe a paid
 * person's Pro just for being offline.
 */
@Singleton
class FirestoreEntitlementSource @Inject constructor() : EntitlementSource {

    private fun signedInUid(): Flow<String?> = callbackFlow {
        val auth = FirebaseAuth.getInstance()
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<Entitlement?> = signedInUid().flatMapLatest { uid -> documentOf(uid) }

    private fun documentOf(uid: String?): Flow<Entitlement?> = callbackFlow {
        if (uid == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val registration = FirebaseFirestore.getInstance().collection("entitlements").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                if (snapshot.exists()) {
                    trySend(EntitlementCodec.fromFields(snapshot.data.orEmpty()))
                } else if (!snapshot.metadata.isFromCache) {
                    trySend(null)
                }
            }
        awaitClose { registration.remove() }
    }
}
