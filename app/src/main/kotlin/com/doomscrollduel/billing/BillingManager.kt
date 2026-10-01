package com.doomscrollduel.billing

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.doomscrollduel.domain.billing.PlanOffer
import com.doomscrollduel.domain.billing.ProPlan
import com.doomscrollduel.domain.billing.ProProduct
import com.doomscrollduel.domain.billing.PurchaseOutcome
import com.doomscrollduel.domain.billing.PurchaseOutcomes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class StoreProblem { PLAY_MISSING, NO_NETWORK, NO_PLANS }

sealed interface StoreState {
    data object Loading : StoreState
    data class Ready(val offers: List<PlanOffer>) : StoreState
    data class Unavailable(val problem: StoreProblem) : StoreState
}

/**
 * Talks to Google Play: lists the two plans with Play's own prices, opens the purchase sheet, and finds existing
 * purchases for "Restore".
 *
 * THIS CLASS NEVER GRANTS PRO. Every purchase token it sees is sent to the server ([PurchaseVerifier]); the server asks
 * Google, and Pro only appears when the server writes the entitlement document that [EntitlementService] reads.
 * That is what makes a hacked or sideloaded app unable to unlock Pro by faking a purchase.
 *
 * Nothing sold here touches the in-app currency; the separation guard script in scripts/ keeps the two apart.
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val verifier: PurchaseVerifier,
    private val accounts: AccountProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val connectLock = Mutex()
    private val _store = MutableStateFlow<StoreState>(StoreState.Loading)
    private val _outcomes = MutableSharedFlow<PurchaseOutcome>(extraBufferCapacity = 8)
    private var details: ProductDetails? = null
    private var lastSyncElapsedMs: Long = -SYNC_EVERY_MS

    val store: StateFlow<StoreState> = _store

    /** Results of purchases that finish after the sheet closes (including payments that were pending for a while). */
    val outcomes: SharedFlow<PurchaseOutcome> = _outcomes

    private val listener = PurchasesUpdatedListener { result, purchases ->
        scope.launch { onPurchasesUpdated(result, purchases.orEmpty()) }
    }

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(listener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    /** Connects if needed. Safe to call again and again; a dropped connection is simply opened again next time. */
    private suspend fun ensureConnected(): Boolean = connectLock.withLock {
        if (client.isReady) return true
        suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() {
                    // The next call reconnects. Nothing to do here.
                }
            })
        }
    }

    /** Loads the plans and their prices from Play. Call when the paywall opens; also the retry button. */
    suspend fun loadOffers() {
        _store.value = StoreState.Loading
        if (!ensureConnected()) {
            _store.value = StoreState.Unavailable(StoreProblem.PLAY_MISSING)
            return
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(ProProduct.PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                ),
            )
            .build()
        val result = client.queryProductDetails(params)
        val code = result.billingResult.responseCode
        if (code != BillingClient.BillingResponseCode.OK) {
            _store.value = StoreState.Unavailable(
                if (code == BillingClient.BillingResponseCode.NETWORK_ERROR || code == BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE) {
                    StoreProblem.NO_NETWORK
                } else {
                    StoreProblem.PLAY_MISSING
                },
            )
            return
        }
        val product = result.productDetailsList?.firstOrNull { it.productId == ProProduct.PRODUCT_ID }
        details = product
        val offers = product?.let(::offersOf).orEmpty()
        _store.value = if (offers.isEmpty()) StoreState.Unavailable(StoreProblem.NO_PLANS) else StoreState.Ready(offers)
    }

    /** One offer per plan: the plain base plan (no promo offer), priced by its recurring phase. */
    private fun offersOf(product: ProductDetails): List<PlanOffer> =
        product.subscriptionOfferDetails.orEmpty().mapNotNull { offer ->
            val plan = ProProduct.planOf(offer.basePlanId) ?: return@mapNotNull null
            if (offer.offerId != null) return@mapNotNull null
            val phase = offer.pricingPhases.pricingPhaseList.lastOrNull() ?: return@mapNotNull null
            PlanOffer(plan, phase.formattedPrice, phase.priceAmountMicros, phase.priceCurrencyCode, offer.offerToken)
        }.sortedBy { it.plan.ordinal }

    /**
     * Opens the Play purchase sheet for [plan]. The answer arrives on [outcomes] after the person finishes.
     * Returns an outcome right away only when the sheet could not open.
     */
    fun buy(activity: Activity, plan: ProPlan): PurchaseOutcome? {
        val uid = accounts.uid() ?: return PurchaseOutcome.NotSignedIn
        val product = details ?: return PurchaseOutcome.StoreUnavailable
        val offer = (store.value as? StoreState.Ready)?.offers?.firstOrNull { it.plan == plan } ?: return PurchaseOutcome.StoreUnavailable
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .setOfferToken(offer.offerToken)
                        .build(),
                ),
            )
            // Ties this purchase to this account. The server refuses a token whose id is not the caller's.
            .setObfuscatedAccountId(uid)
            .build()
        val result = client.launchBillingFlow(activity, params)
        return when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> null
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                scope.launch { _outcomes.emit(restore()) }
                null
            }
            BillingClient.BillingResponseCode.NETWORK_ERROR,
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> PurchaseOutcome.NoNetwork
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> PurchaseOutcome.StoreUnavailable
            else -> PurchaseOutcome.Failed("code ${result.responseCode}")
        }
    }

    /**
     * Finds the subscription Google has on this account (after a reinstall, a new phone, or a purchase that finished
     * while the app was closed) and sends it to the server. Safe to call at any time.
     */
    suspend fun restore(): PurchaseOutcome {
        if (accounts.uid() == null) return PurchaseOutcome.NotSignedIn
        if (!ensureConnected()) return PurchaseOutcome.StoreUnavailable
        val result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        )
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            return if (result.billingResult.responseCode == BillingClient.BillingResponseCode.NETWORK_ERROR) PurchaseOutcome.NoNetwork else PurchaseOutcome.StoreUnavailable
        }
        val ours = result.purchasesList.filter { ProProduct.PRODUCT_ID in it.products }
        return PurchaseOutcomes.best(ours.map { handle(it) })
    }

    /** Quiet re-check, at most every 6 hours, for the app start: picks up renewals, a restored purchase, a new phone. */
    fun syncIfStale() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSyncElapsedMs < SYNC_EVERY_MS) return
        lastSyncElapsedMs = now
        scope.launch { restore() }
    }

    private suspend fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK ->
                _outcomes.emit(PurchaseOutcomes.best(purchases.filter { ProProduct.PRODUCT_ID in it.products }.map { handle(it) }))
            BillingClient.BillingResponseCode.USER_CANCELED -> _outcomes.emit(PurchaseOutcome.Cancelled)
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> _outcomes.emit(restore())
            BillingClient.BillingResponseCode.NETWORK_ERROR,
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> _outcomes.emit(PurchaseOutcome.NoNetwork)
            else -> _outcomes.emit(PurchaseOutcome.Failed("code ${result.responseCode}"))
        }
    }

    /** A pending purchase waits (no Pro); a finished one goes to the server, which decides. */
    private suspend fun handle(purchase: Purchase): PurchaseOutcome = when (purchase.purchaseState) {
        Purchase.PurchaseState.PENDING -> PurchaseOutcome.Pending
        Purchase.PurchaseState.PURCHASED -> verifier.verify(purchase.purchaseToken)
        else -> PurchaseOutcome.Failed("unspecified state")
    }

    private companion object {
        const val SYNC_EVERY_MS = 6L * 60 * 60 * 1000
    }
}
