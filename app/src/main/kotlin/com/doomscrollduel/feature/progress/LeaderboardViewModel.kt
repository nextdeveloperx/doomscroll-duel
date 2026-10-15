package com.doomscrollduel.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doomscrollduel.domain.social.LeaderboardBoard
import com.doomscrollduel.domain.social.LeaderboardRepository
import com.doomscrollduel.domain.social.LeaderboardUi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val repository: LeaderboardRepository,
) : ViewModel() {

    private val retrySignal = MutableStateFlow(0)
    private val _board = MutableStateFlow(LeaderboardBoard.FRIENDS_WEEKLY)
    val board: StateFlow<LeaderboardBoard> = _board

    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<LeaderboardUi> = _board.flatMapLatest { board ->
        retrySignal.flatMapLatest { repository.board(board) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LeaderboardUi.Loading)

    fun select(board: LeaderboardBoard) {
        _board.value = board
    }

    /** Re-tries a load that errored, and also doubles as "pull to refresh". */
    fun retry() {
        retrySignal.value++
    }

    /** Call when the Progress tab (re)appears, so my own score is fresh before the boards are read. */
    fun refreshMyScore() {
        viewModelScope.launch {
            repository.refreshMyScore()
            retry()
        }
    }
}
