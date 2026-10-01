package com.doomscrollduel.feature.paywall

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doomscrollduel.billing.BillingManager
import com.doomscrollduel.billing.EntitlementService
import com.doomscrollduel.billing.StoreState
import com.doomscrollduel.domain.billing.ProFeature
import com.doomscrollduel.domain.billing.ProPlan
import com.doomscrollduel.domain.billing.ProView
import com.doomscrollduel.domain.billing.PurchaseMessage
import com.doomscrollduel.domain.billing.PurchaseOutcome
import com.doomscrollduel.domain.billing.toMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PaywallBusy { NONE, BUYING, RESTORING }

data class PaywallUiState(
    val store: StoreState,
    val view: ProView,
    val selected: ProPlan,
    val busy: PaywallBusy,
    val message: PurchaseMessage?,
    /** The locked thing the person tapped to get here, so the top of the screen can say why. */
    val trigger: ProFeature?,
)

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val billing: BillingManager,
    entitlements: EntitlementService,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val trigger = savedState.get<String>(ARG_FEATURE)?.let { name -> ProFeature.entries.firstOrNull { it.name == name } }
    private val selected = MutableStateFlow(ProPlan.YEARLY)
    private val busy = MutableStateFlow(PaywallBusy.NONE)
    private val message = MutableStateFlow<PurchaseMessage?>(null)

    val ui: StateFlow<PaywallUiState> = combine(billing.store, entitlements.view, selected, busy, message) { store, view, plan, b, msg ->
        PaywallUiState(store, view, plan, b, msg, trigger)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = PaywallUiState(StoreState.Loading, ProView.Free, ProPlan.YEARLY, PaywallBusy.NONE, null, trigger),
    )

    init {
        viewModelScope.launch { billing.loadOffers() }
        // Results that arrive after the Play sheet closes, including a payment that was pending and later cleared.
        viewModelScope.launch { billing.outcomes.collect(::show) }
    }

    fun select(plan: ProPlan) {
        selected.value = plan
    }

    fun buy(activity: Activity) {
        if (busy.value != PaywallBusy.NONE) return
        message.value = null
        busy.value = PaywallBusy.BUYING
        // A non-null answer means the sheet never opened; otherwise the result comes through `outcomes`.
        billing.buy(activity, selected.value)?.let(::show)
    }

    fun restore() {
        if (busy.value != PaywallBusy.NONE) return
        message.value = null
        busy.value = PaywallBusy.RESTORING
        viewModelScope.launch { show(billing.restore()) }
    }

    fun retryStore() {
        viewModelScope.launch { billing.loadOffers() }
    }

    fun dismissMessage() {
        message.value = null
    }

    private fun show(outcome: PurchaseOutcome) {
        busy.value = PaywallBusy.NONE
        message.value = outcome.toMessage()
    }

    companion object {
        const val ARG_FEATURE = "feature"
    }
}
