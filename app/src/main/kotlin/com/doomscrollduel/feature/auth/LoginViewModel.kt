package com.doomscrollduel.feature.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doomscrollduel.data.auth.GoogleSignInHelper
import com.doomscrollduel.data.auth.GoogleSignInOutcome
import com.doomscrollduel.domain.repository.AuthRepository
import com.google.firebase.FirebaseNetworkException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface LoginUi {
    data object Idle : LoginUi
    data object Working : LoginUi
    data class Problem(val kind: LoginProblem) : LoginUi

    /** Signed in. The app moves on by itself once the profile is known. */
    data object Done : LoginUi
}

enum class LoginProblem { NOT_CONFIGURED, NO_ACCOUNT, NETWORK, FAILED }

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow<LoginUi>(LoginUi.Idle)
    val ui: StateFlow<LoginUi> = _ui

    fun signInWithGoogle(activity: Activity) {
        if (_ui.value == LoginUi.Working) return
        _ui.value = LoginUi.Working
        viewModelScope.launch {
            _ui.value = when (val outcome = GoogleSignInHelper.requestIdToken(activity)) {
                GoogleSignInOutcome.Cancelled -> LoginUi.Idle
                GoogleSignInOutcome.NotConfigured -> LoginUi.Problem(LoginProblem.NOT_CONFIGURED)
                GoogleSignInOutcome.NoAccount -> LoginUi.Problem(LoginProblem.NO_ACCOUNT)
                GoogleSignInOutcome.Failed -> LoginUi.Problem(LoginProblem.FAILED)
                is GoogleSignInOutcome.Token -> {
                    val result = auth.signInWithGoogle(outcome.idToken)
                    when {
                        result.isSuccess -> LoginUi.Done
                        result.exceptionOrNull() is FirebaseNetworkException -> LoginUi.Problem(LoginProblem.NETWORK)
                        else -> LoginUi.Problem(LoginProblem.FAILED)
                    }
                }
            }
        }
    }

    fun dismissProblem() {
        if (_ui.value is LoginUi.Problem) _ui.value = LoginUi.Idle
    }
}
