package com.doomscrollduel.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** The battles list on the Battle tab. */
@HiltViewModel
class BattlesViewModel @Inject constructor(activeBattles: ActiveBattles) : ViewModel() {
    val battles: StateFlow<List<ActiveBattleUi>> =
        activeBattles.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
