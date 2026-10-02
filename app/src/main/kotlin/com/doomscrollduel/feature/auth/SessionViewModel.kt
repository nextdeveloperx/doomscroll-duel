package com.doomscrollduel.feature.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doomscrollduel.domain.repository.AuthRepository
import com.doomscrollduel.domain.social.ProfileRepository
import com.doomscrollduel.domain.social.ProfileState
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Two small facts that outlive a screen: the person chose "Abhi nahi" on the login page (the counter works without an
 * account), and an invite link was opened before they had signed in (accepted once they have).
 */
@Singleton
class SessionPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    var loginSkipped: Boolean
        get() = prefs.getBoolean(KEY_SKIPPED, false)
        set(value) { prefs.edit().putBoolean(KEY_SKIPPED, value).apply() }

    /** The four first-run pages (username, age, permissions, welcome) were finished. */
    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(value) { prefs.edit().putBoolean(KEY_ONBOARDED, value).apply() }

    /** The age typed on the age page. Stays on this phone; 0 means not asked yet. */
    var age: Int
        get() = prefs.getInt(KEY_AGE, 0)
        set(value) { prefs.edit().putInt(KEY_AGE, value).apply() }

    var pendingInvite: String?
        get() = prefs.getString(KEY_PENDING_INVITE, null)
        set(value) { prefs.edit().putString(KEY_PENDING_INVITE, value).apply() }

    private companion object {
        const val KEY_SKIPPED = "login_skipped"
        const val KEY_PENDING_INVITE = "pending_invite"
        const val KEY_ONBOARDED = "onboarding_done"
        const val KEY_AGE = "age"
    }
}

@HiltViewModel
class SessionViewModel @Inject constructor(
    profiles: ProfileRepository,
    private val auth: AuthRepository,
    val prefs: SessionPrefs,
    private val pushTokens: com.doomscrollduel.feature.friends.PushTokenRegistrar,
) : ViewModel() {

    /** Signed out / signed in without a username / ready. Drives where the app sends the person. */
    val profile: StateFlow<ProfileState> = profiles.state.stateIn(viewModelScope, SharingStarted.Eagerly, ProfileState.Loading)

    /**
     * Where the app opens. Decided at once from what is saved on the phone: nobody signed in sees the login page (the app
     * cannot be used without an account), anyone signed in goes straight to Home. A signed-in person who still needs a username is sent
     * on from Home as soon as the profile loads.
     */
    fun startsAtLogin(): Boolean = FirebaseAuth.getInstance().currentUser == null

    /** Signed in but the first-run pages were never finished: open on them instead of Home. */
    fun startsAtOnboarding(): Boolean = FirebaseAuth.getInstance().currentUser != null && !prefs.onboardingDone

    fun signOut() {
        viewModelScope.launch {
            pushTokens.unregister()
            auth.signOut() // the sign-in gate in the nav graph takes the person to the login page
        }
    }
}
