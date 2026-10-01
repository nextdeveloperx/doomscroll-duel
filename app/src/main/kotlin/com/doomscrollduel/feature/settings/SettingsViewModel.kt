package com.doomscrollduel.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doomscrollduel.blocking.BlockingScheduler
import com.doomscrollduel.blocking.BuddyDirectory
import com.doomscrollduel.domain.blocking.BlockingController
import com.doomscrollduel.domain.blocking.BlockingStatus
import com.doomscrollduel.domain.blocking.Buddy
import com.doomscrollduel.domain.blocking.ChangeBlocker
import com.doomscrollduel.domain.blocking.ChangeResult
import com.doomscrollduel.domain.blocking.ObservableBlockingSettings
import com.doomscrollduel.domain.blocking.SettingChange
import com.doomscrollduel.domain.blocking.WindowKind
import com.doomscrollduel.domain.repository.ReelRepository
import com.doomscrollduel.tracking.health.TrackingHealth
import com.doomscrollduel.tracking.health.TrackingHealthMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Why a change was refused, in a form the screen can print. */
sealed interface SettingsMessage {
    data class LockRunning(val remainingMs: Long) : SettingsMessage
    data class WindowRunning(val kind: WindowKind, val remainingMs: Long) : SettingsMessage
    data object NeedsBuddy : SettingsMessage
    data object LimitOutOfRange : SettingsMessage
}

data class SettingsUiState(
    val status: BlockingStatus,
    val health: TrackingHealth,
    val friends: List<Buddy>,
    val message: SettingsMessage?,
)

/**
 * Settings and permission health. The status is re-read every second while the screen is visible, so a running
 * lock counts down on screen and the toggles lock and unlock themselves without any extra wiring.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val controller: BlockingController,
    settings: ObservableBlockingSettings,
    private val healthMonitor: TrackingHealthMonitor,
    buddies: BuddyDirectory,
    private val scheduler: BlockingScheduler,
    private val reels: ReelRepository,
) : ViewModel() {

    private val message = MutableStateFlow<SettingsMessage?>(null)
    private val seconds = flow {
        while (true) {
            emit(Unit)
            delay(1_000L)
        }
    }

    val ui: StateFlow<SettingsUiState> = combine(seconds, settings.flow, healthMonitor.observe(), buddies.friends, message) { _, _, health, friends, msg ->
        SettingsUiState(controller.status(), health, friends, msg)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SettingsUiState(controller.status(), healthMonitor.current(), emptyList(), null),
    )

    /** Applies a change unless the commitment rule refuses it; then tells the screen why. */
    fun change(change: SettingChange) {
        viewModelScope.launch {
            val result = controller.change(change)
            message.value = (result as? ChangeResult.Refused)?.let(::toMessage)
            if (result is ChangeResult.Applied) {
                // Switching the timer-lock on when today is already over the limit locks at once.
                if (change is SettingChange.StrictLockEnabled && change.on) controller.onReelCounted(reels.todayTotal())
                scheduler.schedule()
            }
        }
    }

    fun dismissMessage() {
        message.value = null
    }

    /** Call when the screen comes back to the front: battery and notification status have no change broadcast. */
    fun refreshHealth() = healthMonitor.refresh()

    private fun toMessage(refused: ChangeResult.Refused): SettingsMessage = when (refused.blocker) {
        ChangeBlocker.LOCK_RUNNING -> SettingsMessage.LockRunning(refused.untilMs ?: 0L)
        ChangeBlocker.WINDOW_RUNNING -> SettingsMessage.WindowRunning(
            kind = controller.status().window?.kind ?: WindowKind.BEDTIME,
            remainingMs = refused.untilMs ?: 0L,
        )
        ChangeBlocker.NEEDS_BUDDY -> SettingsMessage.NeedsBuddy
        ChangeBlocker.OUT_OF_RANGE -> SettingsMessage.LimitOutOfRange
    }
}
