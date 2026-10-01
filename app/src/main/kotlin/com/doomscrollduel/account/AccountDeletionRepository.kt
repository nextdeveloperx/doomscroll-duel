package com.doomscrollduel.account

import com.doomscrollduel.data.local.AppDatabase
import com.doomscrollduel.billing.EntitlementCache
import com.doomscrollduel.domain.billing.Entitlement
import com.doomscrollduel.domain.legal.DeletionResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.messaging.FirebaseMessaging
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

interface AccountDeletionRepository {
    /** Deletes the signed-in person's account and everything on the server, then clears the account's data on this phone. */
    suspend fun deleteEverything(): DeletionResult
}

/**
 * Calls the `deleteAccount` Cloud Function, which removes the person's data from every place the server keeps it and
 * finally deletes the sign-in account (see functions/src/deletion.ts and docs/play/account-deletion-page.md).
 *
 * The phone is only cleaned AFTER the server says it is done, so a failure never leaves someone with a half-deleted
 * account and nothing to retry. Retrying is safe: every step is idempotent.
 *
 * NOT run against a deployed function yet.
 */
@Singleton
class FirebaseAccountDeletionRepository @Inject constructor(
    private val database: AppDatabase,
    private val entitlementCache: EntitlementCache,
) : AccountDeletionRepository {

    override suspend fun deleteEverything(): DeletionResult {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) return DeletionResult.NOT_SIGNED_IN
        try {
            FirebaseFunctions.getInstance().getHttpsCallable("deleteAccount")
                .call(mapOf("confirm" to true))
                .await()
        } catch (e: FirebaseFunctionsException) {
            return when (e.code) {
                FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                    if (e.message?.contains("recent-login") == true) DeletionResult.NEEDS_RECENT_LOGIN else DeletionResult.NOT_SIGNED_IN
                FirebaseFunctionsException.Code.UNAVAILABLE,
                FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> DeletionResult.NO_NETWORK
                else -> DeletionResult.FAILED
            }
        } catch (e: IOException) {
            return DeletionResult.NO_NETWORK
        }
        clearThisPhone(auth)
        return DeletionResult.DELETED
    }

    /**
     * Clears what belongs to the deleted account on this phone: reel history, the cached Pro record, the push token
     * and the sign-in. It deliberately does NOT touch the blocking settings or a running lock: deleting an account must
     * not be a way to escape a Strict Lock. It also keeps the Accessibility agreement, which is about this phone.
     */
    private suspend fun clearThisPhone(auth: FirebaseAuth) {
        withContext(Dispatchers.IO) {
            runCatching { database.clearAllTables() }
            entitlementCache.save(Entitlement.None)
        }
        runCatching { FirebaseMessaging.getInstance().deleteToken().await() }
        auth.signOut()
    }
}
