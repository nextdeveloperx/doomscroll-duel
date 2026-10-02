package com.doomscrollduel.feature.duel.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.doomscrollduel.data.social.DuelTracker
import com.doomscrollduel.domain.repository.AuthRepository
import com.doomscrollduel.domain.social.DuelAction
import com.doomscrollduel.domain.social.DuelInfo
import com.doomscrollduel.domain.social.DuelRepository
import com.doomscrollduel.domain.social.DuelStatus
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
import kotlinx.coroutines.launch

/** One real battle: the duel document, both players' counts, and the clock ticking once a second. */
@HiltViewModel
class LiveDuelViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val duels: DuelRepository,
    tracker: DuelTracker,
    auth: AuthRepository,
) : ViewModel() {
    val duelId: String = handle.get<String>(ARG).orEmpty()

    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<LiveDuelUiState?> = auth.session.flatMapLatest { session ->
        val me = session?.uid ?: return@flatMapLatest flowOf(null)
        duels.duel(duelId).flatMapLatest { duel ->
            if (duel == null) {
                flowOf(null)
            } else {
                // The start line is only taken once the battle is running, never while it is still waiting.
                val mine = if (duel.status == DuelStatus.ACTIVE) tracker.myCount(duelId) else flowOf(emptyMap())
                combine(duels.counts(duelId), mine, clock) { counts, local, now ->
                    build(duel, me, counts, local.values.sum(), now)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun cancel(): DuelAction = duels.cancel(duelId)

    companion object {
        const val ARG = "duelId"

        fun build(duel: DuelInfo, me: String, counts: Map<String, Int>, local: Int, now: Long): LiveDuelUiState {
            val over = duel.isOver(now) || duel.status == DuelStatus.FINISHED
            val phase = when {
                duel.status == DuelStatus.PENDING -> LivePhase.WAITING
                over -> LivePhase.ENDED
                duel.status == DuelStatus.ACTIVE -> LivePhase.ACTIVE
                else -> LivePhase.CLOSED
            }
            val uploaded = counts[me] ?: 0
            // While it runs this phone's own number is the freshest; once it is over only the uploaded final count is valid.
            val mine = if (phase == LivePhase.ACTIVE) maxOf(local, uploaded) else uploaded
            val theirs = counts[duel.otherUid(me)] ?: 0
            return LiveDuelUiState(
                me = LiveFighter("", mine),
                opponent = LiveFighter(duel.otherName(me), theirs),
                reelLimit = duel.reelLimit,
                timeLeft = when (phase) {
                    LivePhase.ACTIVE -> clock((duel.endAtMs ?: now) - now)
                    LivePhase.WAITING -> "--:--:--"
                    else -> "00:00:00"
                },
                stakeCoins = duel.stakeCoins,
                phase = phase,
                duelId = duel.id,
                canCancel = phase == LivePhase.WAITING && duel.challengerUid == me,
            )
        }

        fun clock(remainingMs: Long): String {
            val seconds = (remainingMs / 1_000).coerceAtLeast(0)
            return "%02d:%02d:%02d".format(seconds / 3_600, seconds % 3_600 / 60, seconds % 60)
        }
    }
}

@Composable
fun LiveDuelRoute(
    onSeeResult: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LiveDuelViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val current = state
    if (current == null) {
        LiveDuelMissing(onClose = onClose, modifier = modifier)
        return
    }
    // The battle is over: its result opens by itself, nobody has to press anything.
    LaunchedEffect(current.phase) { if (current.phase == LivePhase.ENDED) onSeeResult(viewModel.duelId) }
    LiveDuelScreen(
        state = current,
        onRoast = { /* roast messages arrive with the push milestone */ },
        onSeeResult = { onSeeResult(viewModel.duelId) },
        modifier = modifier,
        onCancel = { scope.launch { if (viewModel.cancel() == DuelAction.OK) onClose() } },
        onLeave = { scope.launch { if (viewModel.cancel() == DuelAction.OK) onClose() } },
        onClose = onClose,
    )
}
