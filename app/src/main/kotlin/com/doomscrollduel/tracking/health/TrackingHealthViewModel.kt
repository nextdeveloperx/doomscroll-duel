package com.doomscrollduel.tracking.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Live accessibility and battery status for the Settings and battery-guide screens. */
@HiltViewModel
class TrackingHealthViewModel @Inject constructor(
    private val monitor: TrackingHealthMonitor,
) : ViewModel() {
    val health: StateFlow<TrackingHealth> = monitor.observe().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = monitor.current(),
    )

    fun refresh() = monitor.refresh()
}
