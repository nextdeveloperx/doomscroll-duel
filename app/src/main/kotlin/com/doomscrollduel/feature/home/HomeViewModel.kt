package com.doomscrollduel.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doomscrollduel.core.designsystem.brain.BrainState
import com.doomscrollduel.domain.blocking.BlockingController
import com.doomscrollduel.domain.model.DailyReelStats
import com.doomscrollduel.domain.repository.ReelLimitStore
import com.doomscrollduel.domain.repository.ReelRepository
import com.doomscrollduel.domain.social.DuelStatus
import com.doomscrollduel.tracking.health.TrackingHealthMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: ReelRepository,
    limitStore: ReelLimitStore,
    private val healthMonitor: TrackingHealthMonitor,
    private val blocking: BlockingController,
    activeBattles: ActiveBattles,
) : ViewModel() {

    /** A bedtime or focus window changes at most every few minutes, so a slow tick is plenty. */
    private val windows = flow {
        while (true) {
            emit(blocking.status().window)
            delay(WINDOW_TICK_MS)
        }
    }

    /** The newest battle that is running or waiting. Null when there is none. */
    private val battle: Flow<ActiveBattleUi?> = activeBattles.all.map { it.firstOrNull() }.onStart { emit(null) }

    /** Everything the Home screen shows. Updates live as reels are counted. */
    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            repository.observeToday(),
            limitStore.limit,
            repository.observeStreak(limitStore.limit),
            healthMonitor.observe(),
            windows,
        ) { stats, limit, streak, health, window ->
            HomeStateMapper.map(stats, limit, streak, health, FakeProfile.current, null, window)
        },
        battle,
    ) { state, activeBattle -> state.copy(battle = activeBattle) }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = HomeStateMapper.map(
            stats = DailyReelStats.empty(java.time.LocalDate.now()),
            limit = ReelLimitStore.DEFAULT,
            streak = 0,
            health = healthMonitor.current(),
            profile = FakeProfile.current,
            battle = null,
        ),
    )

    /** Live reel count for today. */
    val reelsToday: Flow<Int> = uiState.map { it.reelsToday }.distinctUntilChanged()

    /** Live brain state: HAPPY up to 40 percent of the limit, FRIED to 99, ZOMBIE at 100 or more. */
    val brainState: Flow<BrainState> = uiState.map { it.brainState }.distinctUntilChanged()

    /** Call when the screen comes back to the foreground; battery status has no change broadcast. */
    fun refreshHealth() = healthMonitor.refresh()

    private companion object {
        const val WINDOW_TICK_MS = 30_000L
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
