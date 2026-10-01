package com.doomscrollduel.feature.legal

import androidx.lifecycle.ViewModel
import com.doomscrollduel.domain.legal.AccessibilityConsent
import com.doomscrollduel.domain.legal.ConsentStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Answers one question for every screen that has a "turn on counting" button: may the Android Accessibility settings be
 * opened right now? Only after the person agreed to the disclosure. Otherwise the button must show the disclosure.
 */
@HiltViewModel
class AccessibilityEntryViewModel @Inject constructor(
    private val consent: ConsentStore,
) : ViewModel() {
    fun mayOpenSettings(): Boolean = AccessibilityConsent.mayRequestPermission(consent.current())
}
