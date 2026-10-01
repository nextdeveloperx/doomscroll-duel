package com.doomscrollduel.feature.paywall

import androidx.lifecycle.ViewModel
import com.doomscrollduel.billing.EntitlementService
import com.doomscrollduel.domain.billing.ProView
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** Gives any screen the current Pro answer, for showing PRO tags and soft upgrade prompts. */
@HiltViewModel
class ProAccessViewModel @Inject constructor(entitlements: EntitlementService) : ViewModel() {
    val view: StateFlow<ProView> = entitlements.view
}
