package com.doomscrollduel.feature.legal

import androidx.lifecycle.ViewModel
import com.doomscrollduel.domain.legal.AccessibilityConsent
import com.doomscrollduel.domain.legal.ConsentStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DisclosureViewModel @Inject constructor(
    private val consent: ConsentStore,
) : ViewModel() {

    /** Records the agreement. Returns true when the Accessibility settings may now be opened. */
    fun agree(): Boolean {
        consent.save(AccessibilityConsent.accept(System.currentTimeMillis()))
        return AccessibilityConsent.mayRequestPermission(consent.current())
    }
}
