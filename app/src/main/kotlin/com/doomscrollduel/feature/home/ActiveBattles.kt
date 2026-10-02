package com.doomscrollduel.feature.home

import com.doomscrollduel.data.social.DuelTracker
import com.doomscrollduel.domain.repository.AuthRepository
import com.doomscrollduel.domain.social.DuelInfo
import com.doomscrollduel.domain.social.DuelRepository
import com.doomscrollduel.domain.social.DuelStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * Every battle that is running now, plus every challenge I sent that has not been answered, newest first, each with live
 * counts. Home shows the first one; the Battle tab lists them all, so an old battle can never hide a newer one.
 */
@Singleton
class ActiveBattles @Inject constructor(
    private val auth: AuthRepository,
    private val duels: DuelRepository,
    private val tracker: DuelTracker,
) {
    private val minute = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000L)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val all: Flow<List<ActiveBattleUi>> = auth.session.flatMapLatest { session ->
        val me = session?.uid ?: return@flatMapLatest flowOf(emptyList())
        duels.duels.flatMapLatest { list ->
            val now = System.currentTimeMillis()
            val relevant = list.filter { duel ->
                when {
                    duel.status == DuelStatus.ACTIVE && !duel.isOver(now) -> true
                    duel.status == DuelStatus.PENDING -> duel.challengerUid == me
                    // Over (by the clock, or marked finished) and the result not opened yet: it stays until it is looked at.
                    duel.status == DuelStatus.ACTIVE || duel.status == DuelStatus.FINISHED -> !tracker.resultSeen(duel.id)
                    else -> false
                }
            }
            if (relevant.isEmpty()) flowOf(emptyList()) else combine(relevant.map { one(it, me) }) { it.toList() }
        }
    }

    private fun one(duel: DuelInfo, me: String): Flow<ActiveBattleUi> {
        val over = duel.status == DuelStatus.FINISHED || duel.isOver(System.currentTimeMillis())
        val mine = if (duel.status == DuelStatus.ACTIVE && !over) tracker.myCount(duel.id) else flowOf(emptyMap())
        return combine(duels.counts(duel.id), mine, minute) { counts, local, t ->
            ActiveBattleUi(
                opponentName = duel.otherName(me),
                // Once it is over only the final uploaded number counts; the phone's own number keeps growing.
                myReels = if (over) counts[me] ?: 0 else maxOf(local.values.sum(), counts[me] ?: 0),
                opponentReels = counts[duel.otherUid(me)] ?: 0,
                timeLeft = timeLeft((duel.endAtMs ?: t) - t),
                stakeCoins = duel.stakeCoins,
                duelId = duel.id,
                waiting = duel.status == DuelStatus.PENDING,
                finished = over,
            )
        }
    }

    private fun timeLeft(remainingMs: Long): String {
        val minutes = (remainingMs / 60_000).coerceAtLeast(0)
        val hours = minutes / 60
        return if (hours >= 24) "${hours / 24}d ${hours % 24}h" else "${hours}h ${minutes % 60}m"
    }
}
