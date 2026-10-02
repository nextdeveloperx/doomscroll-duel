package com.doomscrollduel.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doomscrollduel.domain.analytics.Analytics
import com.doomscrollduel.domain.analytics.AnalyticsEvent
import com.doomscrollduel.domain.onboarding.AgeRule
import com.doomscrollduel.domain.social.ProfileRepository
import com.doomscrollduel.domain.social.ProfileState
import com.doomscrollduel.feature.auth.SessionPrefs
import com.doomscrollduel.tracking.health.TrackingHealth
import com.doomscrollduel.tracking.health.TrackingHealthMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** What the four first-run pages need: the profile, which permissions are on, the age, and "finished". */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    profiles: ProfileRepository,
    private val health: TrackingHealthMonitor,
    private val prefs: SessionPrefs,
    private val analytics: Analytics,
) : ViewModel() {
    val profile: StateFlow<ProfileState> = profiles.state.stateIn(viewModelScope, SharingStarted.Eagerly, ProfileState.Loading)

    val permissions: StateFlow<TrackingHealth> =
        health.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), health.current())

    /** Battery status has no change broadcast, so ask again when the person comes back from a system screen. */
    fun refreshPermissions() = health.refresh()

    val savedAge: Int get() = prefs.age.takeIf { it > 0 }?.let(AgeRule::clamp) ?: AgeRule.DEFAULT_AGE

    fun saveAge(age: Int) {
        prefs.age = age
    }

    /** Marks the first-run pages as finished. [skipped] is how many permissions the person left off, for the anonymous event. */
    fun finish(skipped: Int) {
        prefs.onboardingDone = true
        analytics.track(AnalyticsEvent.OnboardingCompleted(skipped))
    }

    val pendingInvite: String? get() = prefs.pendingInvite
}
