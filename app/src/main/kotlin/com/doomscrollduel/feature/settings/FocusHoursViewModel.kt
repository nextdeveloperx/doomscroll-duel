package com.doomscrollduel.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doomscrollduel.billing.EntitlementService
import com.doomscrollduel.blocking.BlockingScheduler
import com.doomscrollduel.domain.billing.Access
import com.doomscrollduel.domain.billing.SettingGate
import com.doomscrollduel.domain.blocking.BlockingController
import com.doomscrollduel.domain.blocking.ChangeResult
import com.doomscrollduel.domain.blocking.FocusScheduleEditor
import com.doomscrollduel.domain.blocking.FocusSettings
import com.doomscrollduel.domain.blocking.ObservableBlockingSettings
import com.doomscrollduel.domain.blocking.SettingChange
import com.doomscrollduel.domain.blocking.TimeRange
import com.doomscrollduel.domain.blocking.TimeRanges
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class FocusUiState(
    val focus: FocusSettings,
    val selectedDay: DayOfWeek,
    val message: FocusMessage?,
)

sealed interface FocusMessage {
    data class Invalid(val problem: FocusScheduleEditor.Problem) : FocusMessage

    /** A focus window is running now; the schedule is locked until it ends. */
    data class Running(val remainingMs: Long) : FocusMessage

    /** Adding or moving ranges is Pro. Trimming is always free. */
    data object NeedsPro : FocusMessage
}

/** Edits the per-weekday focus schedule. Every edit goes through [FocusScheduleEditor] and the commitment rule. */
@HiltViewModel
class FocusHoursViewModel @Inject constructor(
    private val controller: BlockingController,
    settings: ObservableBlockingSettings,
    private val scheduler: BlockingScheduler,
    private val entitlements: EntitlementService,
) : ViewModel() {

    private val selected = MutableStateFlow(DayOfWeek.MONDAY)
    private val message = MutableStateFlow<FocusMessage?>(null)

    val ui: StateFlow<FocusUiState> = combine(settings.flow, selected, message) { s, day, msg ->
        FocusUiState(s.focus, day, msg)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = FocusUiState(settings.current().focus, DayOfWeek.MONDAY, null),
    )

    fun selectDay(day: DayOfWeek) {
        selected.value = day
        message.value = null
    }

    fun addRange() {
        val day = selected.value
        edit { FocusScheduleEditor.add(it, day, DEFAULT_RANGE) }
    }

    fun nudge(index: Int, moveStart: Boolean, deltaMinutes: Long) {
        val day = selected.value
        edit { focus ->
            val current = focus.rangesOn(day).getOrNull(index) ?: return@edit FocusScheduleEditor.Result.Ok(focus)
            val next = if (moveStart) TimeRanges.nudgeStart(current, deltaMinutes) else TimeRanges.nudgeEnd(current, deltaMinutes)
            FocusScheduleEditor.replace(focus, day, index, next)
        }
    }

    fun remove(index: Int) {
        val day = selected.value
        edit { FocusScheduleEditor.Result.Ok(FocusScheduleEditor.remove(it, day, index)) }
    }

    fun dismissMessage() {
        message.value = null
    }

    private fun edit(block: (FocusSettings) -> FocusScheduleEditor.Result) {
        val current = controller.status().settings.focus
        when (val r = block(current)) {
            is FocusScheduleEditor.Result.Invalid -> message.value = FocusMessage.Invalid(r.problem)
            is FocusScheduleEditor.Result.Ok -> if (SettingGate.check(SettingChange.FocusChanged(r.focus), controller.status().settings, entitlements.isPro) is Access.NeedsPro) {
                message.value = FocusMessage.NeedsPro
            } else when (val applied = controller.change(SettingChange.FocusChanged(r.focus))) {
                is ChangeResult.Applied -> {
                    message.value = null
                    scheduler.schedule()
                }
                is ChangeResult.Refused -> message.value = FocusMessage.Running(applied.untilMs ?: 0L)
            }
        }
    }

    private companion object {
        /** A sensible starting range for a new time: 4 PM to 6 PM. */
        val DEFAULT_RANGE = TimeRange(LocalTime.of(16, 0), LocalTime.of(18, 0))
    }
}
