package com.doomscrollduel.feature.duel.result

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.doomscrollduel.core.common.shareText
import com.doomscrollduel.data.social.DuelTracker
import com.doomscrollduel.domain.repository.AuthRepository
import com.doomscrollduel.domain.social.DuelRepository
import com.doomscrollduel.domain.social.DuelStatus
import com.doomscrollduel.feature.duel.live.LiveDuelMissing
import com.doomscrollduel.feature.duel.live.LiveDuelViewModel
import com.doomscrollduel.feature.duel.live.LivePhase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** The score of one real battle, from the duel document and the two players' counts (the same numbers on both phones). */
@HiltViewModel
class ResultViewModel @Inject constructor(
    handle: SavedStateHandle,
    duels: DuelRepository,
    private val tracker: DuelTracker,
    auth: AuthRepository,
) : ViewModel() {
    private val duelId: String = handle.get<String>(LiveDuelViewModel.ARG).orEmpty()

    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(5_000)
        }
    }

    /** The result was shown, so the battle leaves Home and the Battle tab. */
    fun markSeen() = tracker.markResultSeen(duelId)

    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<ResultUiState?> = auth.session.flatMapLatest { session ->
        val me = session?.uid ?: return@flatMapLatest flowOf(null)
        duels.duel(duelId).flatMapLatest { duel ->
            if (duel == null) {
                flowOf(null)
            } else {
                val mine = if (duel.status == DuelStatus.ACTIVE) tracker.myCount(duelId) else flowOf(emptyMap())
                combine(duels.counts(duelId), mine, clock) { counts, local, now ->
                    val live = LiveDuelViewModel.build(duel, me, counts, local.values.sum(), now)
                    ResultUiState(
                        me = ResultFighter("", live.me.reels),
                        opponent = ResultFighter(live.opponent.name, live.opponent.reels),
                        reelLimit = live.reelLimit,
                        stakeCoins = live.stakeCoins,
                        // Wallets are written by the server only, so no coins move yet; the screen says so.
                        coinsSettled = false,
                        inProgress = live.phase == LivePhase.ACTIVE || live.phase == LivePhase.WAITING,
                    )
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun ResultRoute(
    onRematch: () -> Unit,
    onClose: () -> Unit,
    /** Called once when a finished battle's result is on screen (used for the anonymous result event). */
    onFinishedShown: (ResultUiState) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ResultViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val current = state
    if (current == null) {
        LiveDuelMissing(onClose = onClose, modifier = modifier)
        return
    }
    val finished = !current.inProgress
    LaunchedEffect(finished) {
        if (finished) {
            viewModel.markSeen()
            onFinishedShown(current)
        }
    }
    val shareText = rememberShareText(current)
    ResultScreen(
        state = current,
        onShare = { context.shareText(shareText) },
        onRematch = onRematch,
        modifier = modifier,
    )
}
