package com.doomscrollduel.billing

import com.doomscrollduel.domain.billing.EntitlementStatus
import com.doomscrollduel.domain.billing.PurchaseOutcome
import com.doomscrollduel.domain.billing.PurchaseOutcomes
import com.doomscrollduel.domain.billing.ServerVerdict
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/** Who is signed in. Purchases are tied to this id, so buying needs it. */
interface AccountProvider {
    fun uid(): String?
}

@Singleton
class FirebaseAccountProvider @Inject constructor() : AccountProvider {
    override fun uid(): String? = FirebaseAuth.getInstance().currentUser?.uid
}

/** Sends a Play purchase token to the server and returns what the server decided. */
interface PurchaseVerifier {
    suspend fun verify(purchaseToken: String): PurchaseOutcome
}

/**
 * Calls the `verifyPurchase` Cloud Function. The function asks Google Play about the token, checks it was bought by
 * THIS account, acknowledges it, and writes the entitlement document. Nothing in this class grants Pro.
 *
 * NOT run against a deployed function yet.
 */
@Singleton
class FirebaseFunctionsPurchaseVerifier @Inject constructor(
    private val crashes: com.doomscrollduel.analytics.CrashReporter,
) : PurchaseVerifier {
    override suspend fun verify(purchaseToken: String): PurchaseOutcome {
        if (FirebaseAuth.getInstance().currentUser == null) return PurchaseOutcome.NotSignedIn
        return try {
            val data = FirebaseFunctions.getInstance().getHttpsCallable("verifyPurchase")
                .call(mapOf("purchaseToken" to purchaseToken))
                .await()
                .data as? Map<*, *>
            val status = (data?.get("status") as? String)?.let { s -> EntitlementStatus.entries.firstOrNull { it.name == s } }
            val until = (data?.get("accessUntilMs") as? Number)?.toLong()
            val now = (data?.get("serverNowMs") as? Number)?.toLong()
            if (status == null || until == null || now == null) PurchaseOutcome.Failed("bad answer")
            else PurchaseOutcomes.fromVerdict(ServerVerdict(status, until, now))
        } catch (e: FirebaseFunctionsException) {
            when (e.code) {
                FirebaseFunctionsException.Code.UNAUTHENTICATED -> PurchaseOutcome.NotSignedIn
                FirebaseFunctionsException.Code.PERMISSION_DENIED,
                FirebaseFunctionsException.Code.ALREADY_EXISTS,
                FirebaseFunctionsException.Code.FAILED_PRECONDITION,
                FirebaseFunctionsException.Code.NOT_FOUND -> PurchaseOutcome.Rejected
                FirebaseFunctionsException.Code.UNAVAILABLE,
                FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> PurchaseOutcome.NoNetwork
                else -> {
                    crashes.nonFatal("purchase_verify", e) // class name and stack only, never the message
                    PurchaseOutcome.Failed(e.code.name)
                }
            }
        } catch (e: IOException) {
            PurchaseOutcome.NoNetwork
        }
    }
}
