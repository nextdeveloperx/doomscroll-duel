package com.doomscrollduel.feature.profile

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doomscrollduel.R
import com.doomscrollduel.domain.social.ProfileRepository
import com.doomscrollduel.domain.social.ProfileState
import com.doomscrollduel.feature.friends.PushTokenRegistrar
import com.doomscrollduel.feature.auth.SessionPrefs
import com.doomscrollduel.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUi(
    val state: ProfileState = ProfileState.Loading,
    /** The Google email, shown to its owner only. */
    val email: String? = null,
    val editing: Boolean = false,
    val draftName: String = "",
    val saving: Boolean = false,
    val askLogout: Boolean = false,
    val loggingOut: Boolean = false,
    @StringRes val message: Int? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val auth: AuthRepository,
    private val pushTokens: PushTokenRegistrar,
    private val prefs: SessionPrefs,
) : ViewModel() {

    private val local = MutableStateFlow(ProfileUi())

    val ui: StateFlow<ProfileUi> = combine(local, profiles.state) { state, profile ->
        state.copy(state = profile, email = if (profile == ProfileState.SignedOut) null else FirebaseAuth.getInstance().currentUser?.email)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUi())

    fun startEdit(currentName: String) = local.update { it.copy(editing = true, draftName = currentName, message = null) }

    fun onDraft(text: String) = local.update { it.copy(draftName = text.take(MAX_NAME), message = null) }

    fun cancelEdit() = local.update { it.copy(editing = false, message = null) }

    fun saveName() {
        val name = local.value.draftName.trim()
        if (name.isEmpty() || local.value.saving) return
        local.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            val ok = profiles.updateDisplayName(name)
            local.update { if (ok) it.copy(saving = false, editing = false) else it.copy(saving = false, message = R.string.profile_save_failed) }
        }
    }

    fun askLogout() = local.update { it.copy(askLogout = true) }

    fun cancelLogout() = local.update { it.copy(askLogout = false) }

    /** Takes this phone off the account (push address included), signs out, and calls [done] so the screen can leave. */
    fun logout(done: () -> Unit) {
        if (local.value.loggingOut) return
        local.update { it.copy(loggingOut = true) }
        viewModelScope.launch {
            pushTokens.unregister()
            auth.signOut()
            local.update { it.copy(loggingOut = false, askLogout = false) }
            done()
        }
    }

    private companion object {
        const val MAX_NAME = 30
    }
}
