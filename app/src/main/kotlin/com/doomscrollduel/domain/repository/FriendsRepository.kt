package com.doomscrollduel.domain.repository

import com.doomscrollduel.domain.model.InviteCode
import com.doomscrollduel.domain.model.PhoneNumber
import com.doomscrollduel.domain.model.Username
import kotlinx.coroutines.flow.Flow

data class Friend(val uid: String, val username: String, val displayName: String)

sealed interface AddFriendResult {
    data class Added(val friend: Friend) : AddFriendResult
    data object NotFound : AddFriendResult
    data object AlreadyFriends : AddFriendResult
    data object CannotAddSelf : AddFriendResult
}

sealed interface AcceptInviteResult {
    data class Accepted(val friend: Friend) : AcceptInviteResult
    data object InvalidOrExpired : AcceptInviteResult
    data object AlreadyFriends : AcceptInviteResult
}

/** Friends are created only on the server (Cloud Function), never by writing both sides from the client. */
interface FriendsRepository {
    val friends: Flow<List<Friend>>

    suspend fun addByUsername(username: Username): AddFriendResult

    /** Asks the server for a fresh invite code to put in a link. */
    suspend fun createInvite(): InviteCode

    suspend fun acceptInvite(code: InviteCode): AcceptInviteResult
}

/** An opponent's count for today, shown only while a duel between the two players is active. */
data class OpponentToday(val duelId: String, val uid: String, val reelsToday: Int, val updatedAtMillis: Long)

interface DuelLiveRepository {
    /**
     * Emits the opponent's count while the duel is active, and null otherwise (not started, finished,
     * or the server refuses the read). Firestore rules enforce this; the app does not hide anything itself.
     */
    fun observeOpponentToday(duelId: String): Flow<OpponentToday?>
}

data class AuthSession(val uid: String)

interface AuthRepository {
    val session: Flow<AuthSession?>

    suspend fun signInWithGoogle(idToken: String): Result<AuthSession>

    /** Sends the SMS code. */
    suspend fun startPhoneSignIn(phone: PhoneNumber): Result<Unit>

    suspend fun confirmPhoneCode(code: String): Result<AuthSession>

    suspend fun signOut()
}
