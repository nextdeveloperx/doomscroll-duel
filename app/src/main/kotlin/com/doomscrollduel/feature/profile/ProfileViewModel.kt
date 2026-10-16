package com.doomscrollduel.feature.profile

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doomscrollduel.R
import com.doomscrollduel.domain.social.NameStyle
import com.doomscrollduel.domain.social.ProfileExtras
import com.doomscrollduel.domain.social.ProfileRepository
import com.doomscrollduel.domain.social.SocialApp
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
    /** The plain letters being typed; the chosen [nameStyle] is applied when saving. */
    val draftName: String = "",
    val nameStyle: NameStyle = NameStyle.NORMAL,
    /** My saved bio and social handles (shown on my profile), and the copy being edited. */
    val extras: ProfileExtras = ProfileExtras(),
    val draftExtras: ProfileExtras = ProfileExtras(),
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

    private var extrasLoadedFor: String? = null

    /** Reads my bio and handles once per signed-in person. */
    fun loadExtras(uid: String) {
        if (extrasLoadedFor == uid) return
        extrasLoadedFor = uid
        viewModelScope.launch {
            val loaded = profiles.extrasOf(uid)
            if (loaded == null) extrasLoadedFor = null else local.update { it.copy(extras = loaded) }
        }
    }

    fun startEdit(currentName: String) = local.update {
        it.copy(editing = true, draftName = currentName, nameStyle = NameStyle.NORMAL, draftExtras = it.extras, message = null)
    }

    fun onDraft(text: String) = local.update { it.copy(draftName = NameStyle.fit(text, it.nameStyle), message = null) }

    fun onStyle(style: NameStyle) = local.update { it.copy(nameStyle = style, draftName = NameStyle.fit(it.draftName, style), message = null) }

    fun onBio(text: String) = local.update { it.copy(draftExtras = it.draftExtras.copy(bio = text.take(ProfileExtras.MAX_BIO)), message = null) }

    fun onHandle(app: SocialApp, text: String) =
        local.update { it.copy(draftExtras = it.draftExtras.with(app, text.take(MAX_LINK_INPUT)), message = null) }

    fun cancelEdit() = local.update { it.copy(editing = false, message = null) }

    /** Saves the name (in its chosen style) and the bio and handles. Both must work, or the person is told and can try again. */
    fun saveName() {
        val current = local.value
        val plain = current.draftName.trim()
        if (plain.isEmpty() || current.saving) return
        val styled = current.nameStyle.apply(plain)
        val extras = current.draftExtras.cleaned()
        val shownName = ((current.state as? ProfileState.Ready)?.profile?.displayName)
        local.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            val nameOk = styled == shownName || profiles.updateDisplayName(styled)
            val extrasOk = extras == current.extras || profiles.saveExtras(extras)
            local.update {
                when {
                    nameOk && extrasOk -> it.copy(saving = false, editing = false, extras = extras)
                    else -> it.copy(saving = false, message = R.string.profile_save_failed, extras = if (extrasOk) extras else it.extras)
                }
            }
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
        /** A pasted profile link can be longer than a handle; it is reduced to the handle when saved. */
        const val MAX_LINK_INPUT = 120
    }
}
