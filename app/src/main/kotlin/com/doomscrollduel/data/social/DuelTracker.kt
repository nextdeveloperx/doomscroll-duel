package com.doomscrollduel.data.social

import android.content.Context
import com.doomscrollduel.domain.repository.AuthRepository
import com.doomscrollduel.domain.repository.ReelRepository
import com.doomscrollduel.domain.social.DuelAction
import com.doomscrollduel.domain.social.DuelInfo
import com.doomscrollduel.domain.social.DuelRepository
import com.doomscrollduel.domain.social.DuelStatus
import com.doomscrollduel.feature.friends.FriendNotifications
import com.doomscrollduel.tracking.model.TrackedApp
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Runs the phone's side of every duel while the app process is alive (the reel counter's foreground service keeps it so):
 *
 *  - works out MY count "since the duel started" = reels counted so far minus what had been counted at the start,
 *  - uploads it to `duels/{id}/counts/{me}` as it changes (at most every few seconds; the rules reject a lower number),
 *  - marks a duel finished once its time is over,
 *  - raises a phone notification when somebody challenges me, or accepts my challenge.
 *
 * The start line is taken on the accepting phone the moment the player taps Accept, and on the challenger's phone the first
 * moment it sees the duel active. Only counts are uploaded: no app content, no times of single reels.
 */
@Singleton
class DuelTracker @Inject constructor(
    private val auth: AuthRepository,
    private val duels: DuelRepository,
    private val reels: ReelRepository,
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("duel_tracker", Context.MODE_PRIVATE)
    private val lastUploadedTotal = ConcurrentHashMap<String, Int>()
    private val lastUploadAt = ConcurrentHashMap<String, Long>()
    private val finishing = ConcurrentHashMap.newKeySet<String>()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            auth.session.collectLatest { session -> if (session != null) run(session.uid) }
        }
    }

    /** The result of this battle was opened, so it no longer sits on Home and the Battle tab. */
    fun markResultSeen(duelId: String) {
        prefs.edit().putStringSet(SEEN, (prefs.getStringSet(SEEN, emptySet()).orEmpty() + duelId).toList().takeLast(MAX_REMEMBERED).toSet()).apply()
    }

    fun resultSeen(duelId: String): Boolean = duelId in prefs.getStringSet(SEEN, emptySet()).orEmpty()

    /** Takes "everything counted so far" as this duel's start line on this phone, replacing an older one. */
    suspend fun markStart(duelId: String) {
        saveBaseline(duelId, reels.observeAllTimeByApp().first())
    }

    /** My reels in this duel per app, live. The first time a duel is looked at, the start line is taken now. */
    fun myCount(duelId: String): Flow<Map<TrackedApp, Int>> = reels.observeAllTimeByApp().map { now ->
        if (!hasBaseline(duelId)) saveBaseline(duelId, now)
        sinceStart(duelId, now)
    }

    private suspend fun run(uid: String) {
        val ticker = flow {
            while (true) {
                emit(Unit)
                delay(TICK_MS)
            }
        }
        combine(duels.duels, reels.observeAllTimeByApp(), ticker) { list, totals, _ -> list to totals }.collect { (list, totals) ->
            val now = System.currentTimeMillis()
            list.forEach { duel ->
                when (duel.status) {
                    DuelStatus.PENDING -> if (duel.opponentUid == uid) notifyOnce("c:${duel.id}") {
                        FriendNotifications.showChallenge(context, duel.id, duel.challengerName)
                    }
                    DuelStatus.ACTIVE -> {
                        if (duel.challengerUid == uid) notifyOnce("s:${duel.id}") {
                            FriendNotifications.showDuelStarted(context, duel.id, duel.opponentName)
                        }
                        track(duel, totals, now)
                        if (duel.isOver(now)) endedNotice(duel, uid, now)
                    }
                    DuelStatus.FINISHED -> endedNotice(duel, uid, now)
                    else -> Unit
                }
            }
        }
    }

    /** One notification per battle when it is over, so the result is never something to go and look for. */
    private fun endedNotice(duel: DuelInfo, me: String, now: Long) {
        val end = duel.endAtMs ?: return
        // Only battles that ended in the last day: an old finished battle on a fresh install stays quiet.
        if (now - end > 24 * 3_600_000L) return
        notifyOnce("e:${duel.id}") { FriendNotifications.showDuelEnded(context, duel.id, duel.otherName(me)) }
    }

    private suspend fun track(duel: DuelInfo, totals: Map<TrackedApp, Int>, now: Long) {
        if (!hasBaseline(duel.id)) saveBaseline(duel.id, totals)
        if (duel.isOver(now)) {
            if (finishing.add(duel.id)) {
                if (duels.finish(duel.id) != DuelAction.OK) finishing.remove(duel.id)
            }
            return
        }
        val mine = sinceStart(duel.id, totals)
        val total = mine.values.sum()
        if (lastUploadedTotal[duel.id] == total) return
        if (now - (lastUploadAt[duel.id] ?: 0L) < UPLOAD_GAP_MS) return
        lastUploadAt[duel.id] = now
        val result = duels.uploadCount(duel.id, mine.mapKeys { it.key.name.lowercase() })
        if (result == DuelAction.OK) lastUploadedTotal[duel.id] = total
    }

    // ----- start lines ----------------------------------------------------------------------------------------------

    private fun hasBaseline(id: String): Boolean = prefs.contains("b:$id")

    private fun saveBaseline(id: String, totals: Map<TrackedApp, Int>) {
        val edit = prefs.edit().putBoolean("b:$id", true)
        TrackedApp.entries.forEach { edit.putInt("b:$id:${it.name}", totals[it] ?: 0) }
        edit.apply()
    }

    private fun sinceStart(id: String, totals: Map<TrackedApp, Int>): Map<TrackedApp, Int> =
        TrackedApp.entries.associateWith { app -> ((totals[app] ?: 0) - prefs.getInt("b:$id:${app.name}", 0)).coerceAtLeast(0) }

    private inline fun notifyOnce(key: String, show: () -> Unit) {
        val done = prefs.getStringSet(NOTIFIED, emptySet()).orEmpty()
        if (key in done) return
        show()
        prefs.edit().putStringSet(NOTIFIED, (done + key).toList().takeLast(MAX_REMEMBERED).toSet()).apply()
    }

    private companion object {
        const val TICK_MS = 15_000L
        const val UPLOAD_GAP_MS = 4_000L
        const val NOTIFIED = "notified"
        const val SEEN = "seen_results"
        const val MAX_REMEMBERED = 100
    }
}
