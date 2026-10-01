package com.doomscrollduel.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doomscrollduel.account.AccountDeletionRepository
import com.doomscrollduel.billing.EntitlementService
import com.doomscrollduel.domain.billing.EntitlementStatus
import com.doomscrollduel.domain.legal.DeleteConfirmation
import com.doomscrollduel.domain.legal.DeletionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface DeletePhase {
    data object Form : DeletePhase
    data object Working : DeletePhase
    data object Done : DeletePhase
    data class Error(val result: DeletionResult) : DeletePhase
}

data class DeleteAccountUiState(
    val typed: String = "",
    val understood: Boolean = false,
    val phase: DeletePhase = DeletePhase.Form,
    /** A Pro subscription exists (even if ending): deleting the account does not stop Google Play billing. */
    val hasSubscription: Boolean = false,
) {
    val canDelete: Boolean get() = DeleteConfirmation.canDelete(typed, understood, busy = phase == DeletePhase.Working)
}

@HiltViewModel
class DeleteAccountViewModel @Inject constructor(
    private val repository: AccountDeletionRepository,
    entitlements: EntitlementService,
) : ViewModel() {

    private val _ui = MutableStateFlow(DeleteAccountUiState(hasSubscription = hasLiveSubscription(entitlements.entitlement.value.status)))
    val ui: StateFlow<DeleteAccountUiState> = _ui.asStateFlow()

    fun onTyped(text: String) = _ui.update { if (it.phase == DeletePhase.Working) it else it.copy(typed = text.take(MAX_TYPED)) }

    fun onUnderstood(value: Boolean) = _ui.update { if (it.phase == DeletePhase.Working) it else it.copy(understood = value) }

    fun dismissError() = _ui.update { if (it.phase is DeletePhase.Error) it.copy(phase = DeletePhase.Form) else it }

    fun delete() {
        val state = _ui.value
        if (!state.canDelete) return
        _ui.update { it.copy(phase = DeletePhase.Working) }
        viewModelScope.launch {
            val result = repository.deleteEverything()
            _ui.update { it.copy(phase = if (result == DeletionResult.DELETED) DeletePhase.Done else DeletePhase.Error(result)) }
        }
    }

    private companion object {
        const val MAX_TYPED = 12

        /** Anything that Google Play may still bill for. A subscription is not cancelled by deleting an account. */
        fun hasLiveSubscription(status: EntitlementStatus): Boolean =
            status != EntitlementStatus.NONE && status != EntitlementStatus.EXPIRED
    }
}
