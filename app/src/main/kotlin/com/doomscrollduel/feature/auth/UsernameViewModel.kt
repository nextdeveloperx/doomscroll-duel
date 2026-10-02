package com.doomscrollduel.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doomscrollduel.domain.model.Username
import com.doomscrollduel.domain.model.UsernameProblem
import com.doomscrollduel.domain.model.UsernameResult
import com.doomscrollduel.domain.social.ClaimResult
import com.doomscrollduel.domain.social.ProfileRepository
import com.doomscrollduel.domain.social.UsernameAvailability
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** What the username box says about the text typed so far. */
sealed interface UsernameCheck {
    data object Empty : UsernameCheck
    data class Invalid(val problem: UsernameProblem) : UsernameCheck
    data object Checking : UsernameCheck
    data class Available(val name: String) : UsernameCheck
    data object Taken : UsernameCheck
    data object Offline : UsernameCheck
    data object Error : UsernameCheck
}

data class UsernameUi(
    val text: String = "",
    val check: UsernameCheck = UsernameCheck.Empty,
    val saving: Boolean = false,
    /** Set when saving failed, with a reason the screen turns into a sentence. */
    val saveProblem: ClaimResult? = null,
) {
    val canSave: Boolean get() = check is UsernameCheck.Available && !saving
}

@HiltViewModel
class UsernameViewModel @Inject constructor(
    private val profiles: ProfileRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(UsernameUi())
    val ui: StateFlow<UsernameUi> = _ui
    private var checking: Job? = null

    fun onTextChange(raw: String) {
        // Typing is limited to the characters a username can hold, so the box cannot collect nonsense.
        val text = raw.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '.' }.take(Username.MAX_LENGTH)
        _ui.value = _ui.value.copy(text = text, saveProblem = null)
        checking?.cancel()
        if (text.isEmpty()) {
            _ui.value = _ui.value.copy(check = UsernameCheck.Empty)
            return
        }
        when (val parsed = Username.parse(text)) {
            is UsernameResult.Invalid -> _ui.value = _ui.value.copy(check = UsernameCheck.Invalid(parsed.problem))
            is UsernameResult.Valid -> {
                _ui.value = _ui.value.copy(check = UsernameCheck.Checking)
                checking = viewModelScope.launch {
                    delay(CHECK_DELAY_MS) // wait for the typing to pause, then ask the server
                    val check = when (profiles.checkUsername(parsed.username)) {
                        UsernameAvailability.AVAILABLE -> UsernameCheck.Available(parsed.username.value)
                        UsernameAvailability.TAKEN -> UsernameCheck.Taken
                        UsernameAvailability.NO_NETWORK -> UsernameCheck.Offline
                        UsernameAvailability.INVALID, UsernameAvailability.NOT_SIGNED_IN, UsernameAvailability.FAILED -> UsernameCheck.Error
                    }
                    _ui.value = _ui.value.copy(check = check)
                }
            }
        }
    }

    fun save() {
        val state = _ui.value
        val parsed = (Username.parse(state.text) as? UsernameResult.Valid)?.username ?: return
        if (!state.canSave) return
        _ui.value = state.copy(saving = true, saveProblem = null)
        viewModelScope.launch {
            val name = FirebaseAuth.getInstance().currentUser?.displayName.orEmpty()
            val result = profiles.claimUsername(parsed, name)
            _ui.value = when (result) {
                // The profile listener sees the new profile and the app moves on by itself.
                is ClaimResult.Created, ClaimResult.AlreadyHasProfile -> _ui.value.copy(saving = false)
                ClaimResult.Taken -> _ui.value.copy(saving = false, check = UsernameCheck.Taken)
                else -> _ui.value.copy(saving = false, saveProblem = result)
            }
        }
    }

    private companion object {
        const val CHECK_DELAY_MS = 450L
    }
}
