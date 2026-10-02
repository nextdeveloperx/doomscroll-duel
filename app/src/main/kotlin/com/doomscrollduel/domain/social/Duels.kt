package com.doomscrollduel.domain.social

import com.doomscrollduel.domain.repository.Friend
import kotlinx.coroutines.flow.Flow

enum class DuelStatus { PENDING, ACTIVE, FINISHED, DECLINED, CANCELLED }

/**
 * A 1v1 duel between two friends. It starts [DuelStatus.PENDING] when one challenges the other and becomes
 * [DuelStatus.ACTIVE] when the other accepts; the end is the start plus [hours] (no separate end time is stored).
 */
data class DuelInfo(
    val id: String,
    val challengerUid: String,
    val opponentUid: String,
    val challengerName: String,
    val opponentName: String,
    val status: DuelStatus,
    val reelLimit: Int,
    val hours: Int,
    val stakeCoins: Int,
    val createdAtMs: Long,
    val startAtMs: Long?,
) {
    val endAtMs: Long? get() = startAtMs?.plus(hours * 3_600_000L)

    fun otherUid(me: String): String = if (me == challengerUid) opponentUid else challengerUid

    fun otherName(me: String): String = if (me == challengerUid) opponentName else challengerName

    fun isOver(nowMs: Long): Boolean = status == DuelStatus.ACTIVE && endAtMs?.let { nowMs >= it } == true
}

enum class DuelAction { OK, NO_NETWORK, GONE, NOT_FRIENDS, NOT_SIGNED_IN, FAILED }

data class ChallengeOutcome(val action: DuelAction, val duelId: String? = null)

/**
 * Duels that run on Firestore under the security rules (no Cloud Function needed): challenge, accept, decline, cancel,
 * finish, and the two players' counts. Coins are NOT moved here; wallets are written by the server only.
 */
interface DuelRepository {
    /** Every duel I am in, newest first. Empty while signed out. */
    val duels: Flow<List<DuelInfo>>

    fun duel(id: String): Flow<DuelInfo?>

    /** Each player's reel count in this duel, by uid. Empty until the duel is active. */
    fun counts(id: String): Flow<Map<String, Int>>

    suspend fun challenge(friend: Friend, reelLimit: Int, hours: Int, stakeCoins: Int): ChallengeOutcome

    suspend fun accept(id: String): DuelAction

    suspend fun decline(id: String): DuelAction

    suspend fun cancel(id: String): DuelAction

    suspend fun finish(id: String): DuelAction

    /** Writes my count (the rules check that the total matches the per-app numbers and never goes down). */
    suspend fun uploadCount(id: String, perApp: Map<String, Int>): DuelAction
}
