package com.doomscrollduel.data.auth

import com.doomscrollduel.domain.model.PhoneNumber
import com.doomscrollduel.domain.repository.AuthRepository
import com.doomscrollduel.domain.repository.AuthSession
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

/** Firebase Auth behind [AuthRepository]. Google is the way in; phone sign-in is not built yet. */
@Singleton
class FirebaseAuthRepository @Inject constructor() : AuthRepository {
    private val auth get() = FirebaseAuth.getInstance()

    override val session: Flow<AuthSession?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid?.let(::AuthSession)) }
        FirebaseAuth.getInstance().addAuthStateListener(listener)
        awaitClose { FirebaseAuth.getInstance().removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun signInWithGoogle(idToken: String): Result<AuthSession> = runCatching {
        val user = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await().user
            ?: error("No user after sign-in")
        AuthSession(user.uid)
    }

    override suspend fun startPhoneSignIn(phone: PhoneNumber): Result<Unit> =
        Result.failure(UnsupportedOperationException("Phone sign-in is not available yet."))

    override suspend fun confirmPhoneCode(code: String): Result<AuthSession> =
        Result.failure(UnsupportedOperationException("Phone sign-in is not available yet."))

    override suspend fun signOut() {
        auth.signOut()
    }
}
