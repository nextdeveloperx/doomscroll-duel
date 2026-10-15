package com.doomscrollduel.data.social

import android.util.Log
import com.doomscrollduel.domain.repository.AuthRepository
import com.doomscrollduel.domain.repository.FriendsRepository
import com.doomscrollduel.domain.social.LeaderboardBoard
import com.doomscrollduel.domain.social.LeaderboardEntry
import com.doomscrollduel.domain.social.LeaderboardRepository
import com.doomscrollduel.domain.social.LeaderboardUi
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.time.LocalDate
import java.time.temporal.IsoFields
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

/**
 * Reads the two leaderboards from `directory` (see firestore.rules): Dost is my friends list ordered by
 * `weeklyCoins`, India is everyone ordered by `monthlyCoins`. Not live (a pull or a tab re-entry refetches) —
 * simpler than a listener, and a leaderboard does not need to move the instant a coin does.
 */
@Singleton
class FirebaseLeaderboardRepository @Inject constructor(
    private val auth: AuthRepository,
    private val friends: FriendsRepository,
) : LeaderboardRepository {

    private val db get() = FirebaseFirestore.getInstance()

    override fun board(board: LeaderboardBoard): Flow<LeaderboardUi> = flow {
        emit(LeaderboardUi.Loading)
        val me = auth.session.first()?.uid
        if (me == null) {
            emit(LeaderboardUi.Error(canRetry = false))
            return@flow
        }
        emit(
            try {
                when (board) {
                    LeaderboardBoard.FRIENDS_WEEKLY -> friendsBoard(me)
                    LeaderboardBoard.INDIA_MONTHLY -> indiaBoard(me)
                }
            } catch (e: Exception) {
                Log.w(TAG, "leaderboard load failed", e)
                LeaderboardUi.Error()
            },
        )
    }

    private suspend fun friendsBoard(me: String): LeaderboardUi {
        val friendUids = friends.friends.first().map { it.uid }
        val uids = (friendUids + me).distinct().take(MAX_WHERE_IN)
        val docs = db.collection("directory").whereIn(FieldPath.documentId(), uids).get().await()
        val entries = docs.documents.mapNotNull { it.toEntry(me, "weeklyCoins") }.sortedByDescending { it.coins }
        val myIndex = entries.indexOfFirst { it.isMe }
        return LeaderboardUi.Loaded(
            entries = entries,
            myRank = if (myIndex >= 0) myIndex + 1 else null,
            myEntry = entries.getOrNull(myIndex),
        )
    }

    private suspend fun indiaBoard(me: String): LeaderboardUi {
        val top = db.collection("directory").orderBy("monthlyCoins", Query.Direction.DESCENDING).limit(TOP_N).get().await()
        val entries = top.documents.mapNotNull { it.toEntry(me, "monthlyCoins") }
        val myIndex = entries.indexOfFirst { it.isMe }
        if (myIndex >= 0) return LeaderboardUi.Loaded(entries, myIndex + 1, entries[myIndex])

        // I'm not in the visible top N: one cheap count() instead of fetching everyone, to still show my rank.
        val mine = db.collection("directory").document(me).get().await()
        val myEntry = mine.toEntry(me, "monthlyCoins") ?: return LeaderboardUi.Loaded(entries, null, null)
        val ahead = db.collection("directory").whereGreaterThan("monthlyCoins", myEntry.coins)
            .count().get(AggregateSource.SERVER).await().count
        return LeaderboardUi.Loaded(entries, ahead.toInt() + 1, myEntry)
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toEntry(me: String, scoreField: String): LeaderboardEntry? {
        val username = getString("username") ?: return null
        return LeaderboardEntry(
            uid = id,
            username = username,
            displayName = getString("displayName") ?: username,
            coins = (getLong(scoreField) ?: 0L).toInt(),
            isMe = id == me,
        )
    }

    override suspend fun refreshMyScore() {
        val me = auth.session.first()?.uid ?: return
        try {
            val now = LocalDate.now()
            val weekKey = "${now.get(IsoFields.WEEK_BASED_YEAR)}-W${now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"
            val monthKey = now.toString().substring(0, 7) // "2026-10"
            val weeklyStart = now.with(java.time.DayOfWeek.MONDAY).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
            val monthlyStart = now.withDayOfMonth(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()

            val mine = db.collection("directory").document(me).get().await()
            val prevWeekKey = mine.getString("weeklyPeriodKey")
            val prevMonthKey = mine.getString("monthlyPeriodKey")
            val prevWeekly = mine.getLong("weeklyCoins") ?: 0L
            val prevMonthly = mine.getLong("monthlyCoins") ?: 0L

            val allWon = wonOps(me)
            val weekly = allWon.filter { it.second >= weeklyStart }.sumOf { it.first }
            val monthly = allWon.filter { it.second >= monthlyStart }.sumOf { it.first }

            if (weekKey != prevWeekKey || weekly.toLong() != prevWeekly) {
                db.collection("directory").document(me).update(mapOf("weeklyCoins" to weekly, "weeklyPeriodKey" to weekKey)).await()
            }
            if (monthKey != prevMonthKey || monthly.toLong() != prevMonthly) {
                db.collection("directory").document(me).update(mapOf("monthlyCoins" to monthly, "monthlyPeriodKey" to monthKey)).await()
            }
        } catch (e: Exception) {
            Log.w(TAG, "refreshMyScore failed", e)
        }
    }

    /** Every coin-win (transfer_credit op) I've ever had, each with when it happened. A wallet's op history is
     * bounded by how many duels it has played, so fetching all of them and filtering by date in Kotlin (rather
     * than a composite Firestore index on kind + at) is the simpler, cheap-enough choice here. */
    private suspend fun wonOps(me: String): List<Pair<Int, java.time.Instant>> {
        val ops = db.collection("wallets").document(me).collection("ops").whereEqualTo("kind", "transfer_credit").get().await()
        return ops.documents.map { (it.getLong("amount") ?: 0L).toInt() to (it.getTimestamp("at")?.toInstant() ?: java.time.Instant.EPOCH) }
    }

    private companion object {
        const val TAG = "LeaderboardRepository"
        const val MAX_WHERE_IN = 30
        const val TOP_N = 50L
    }
}
