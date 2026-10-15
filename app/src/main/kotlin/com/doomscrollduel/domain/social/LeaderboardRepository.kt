package com.doomscrollduel.domain.social

import kotlinx.coroutines.flow.Flow

enum class LeaderboardBoard {
    /** Dost: just the people I'm friends with, resets every week. */
    FRIENDS_WEEKLY,

    /** India: every signed-in person, resets every month. */
    INDIA_MONTHLY,
}

data class LeaderboardEntry(
    val uid: String,
    val username: String,
    val displayName: String,
    val coins: Int,
    val isMe: Boolean,
)

sealed interface LeaderboardUi {
    data object Loading : LeaderboardUi
    data class Error(val canRetry: Boolean = true) : LeaderboardUi
    data class Loaded(
        val entries: List<LeaderboardEntry>,
        /** My own rank (1-based) even when I am not among [entries], or null if I have no score yet this period. */
        val myRank: Int?,
        val myEntry: LeaderboardEntry?,
    ) : LeaderboardUi {
        val isEmpty: Boolean get() = entries.isEmpty()
    }
}

interface LeaderboardRepository {
    fun board(board: LeaderboardBoard): Flow<LeaderboardUi>

    /** Recomputes my own coins-won-this-period from my wallet and reports it to the board(s), if it changed. Cheap
     * and safe to call often: a duel settling, the Progress tab opening, or the app coming back to the foreground. */
    suspend fun refreshMyScore()
}
