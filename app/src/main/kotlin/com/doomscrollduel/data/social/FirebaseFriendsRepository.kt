package com.doomscrollduel.data.social

import com.doomscrollduel.domain.model.InviteCode
import com.doomscrollduel.domain.model.Username
import com.doomscrollduel.domain.repository.AcceptInviteResult
import com.doomscrollduel.domain.repository.AddFriendResult
import com.doomscrollduel.domain.repository.AuthRepository
import com.doomscrollduel.domain.repository.Friend
import com.doomscrollduel.domain.repository.FriendsRepository
import com.doomscrollduel.domain.repository.SendInviteResult
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** Friends, invites and personal invites. The server writes every friendship; this class reads the list and asks. */
@Singleton
class FirebaseFriendsRepository @Inject constructor(auth: AuthRepository) : FriendsRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val friends: Flow<List<Friend>> = auth.session.flatMapLatest { session ->
        if (session == null) flowOf(emptyList()) else friendsOf(session.uid)
    }

    private fun friendsOf(uid: String): Flow<List<Friend>> = callbackFlow {
        val registration = FirebaseFirestore.getInstance().collection("users").document(uid).collection("friends")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val list = snapshot.documents.mapNotNull { doc ->
                    val username = doc.getString("username") ?: return@mapNotNull null
                    Friend(doc.id, username, doc.getString("displayName") ?: username)
                }.sortedBy { it.displayName.lowercase() }
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    override suspend fun addByUsername(username: Username): AddFriendResult =
        when (val call = callFunction("addFriendByUsername", mapOf("username" to username.value))) {
            is CallResult.Ok -> when (call.data.string("result")) {
                "added" -> call.data.friend()?.let { AddFriendResult.Added(it) } ?: AddFriendResult.Failed
                "already_friends" -> AddFriendResult.AlreadyFriends
                "not_found" -> AddFriendResult.NotFound
                "self" -> AddFriendResult.CannotAddSelf
                else -> AddFriendResult.Failed
            }
            CallResult.NotSignedIn -> AddFriendResult.NotSignedIn
            CallResult.NoNetwork -> AddFriendResult.NoNetwork
            CallResult.Failed -> AddFriendResult.Failed
        }

    override suspend fun createInvite(): InviteCode? =
        (callFunction("createInvite") as? CallResult.Ok)?.data?.string("code")?.let(InviteCode::parse)

    override suspend fun acceptInvite(code: InviteCode): AcceptInviteResult =
        when (val call = callFunction("acceptInvite", mapOf("code" to code.value))) {
            is CallResult.Ok -> when (call.data.string("result")) {
                "accepted" -> call.data.friend()?.let { AcceptInviteResult.Accepted(it) } ?: AcceptInviteResult.Failed
                "already_friends" -> AcceptInviteResult.AlreadyFriends
                "invalid", "expired", "used_up", "own_invite", "not_for_you" -> AcceptInviteResult.InvalidOrExpired
                else -> AcceptInviteResult.Failed
            }
            CallResult.NotSignedIn -> AcceptInviteResult.NotSignedIn
            CallResult.NoNetwork -> AcceptInviteResult.NoNetwork
            CallResult.Failed -> AcceptInviteResult.Failed
        }

    override suspend fun inviteUser(uid: String): SendInviteResult =
        when (val call = callFunction("inviteUser", mapOf("targetUid" to uid))) {
            is CallResult.Ok -> when (call.data.string("result")) {
                "sent" -> SendInviteResult.SENT
                "already_sent" -> SendInviteResult.ALREADY_SENT
                "already_friends" -> SendInviteResult.ALREADY_FRIENDS
                "not_found" -> SendInviteResult.NOT_FOUND
                else -> SendInviteResult.FAILED
            }
            CallResult.NotSignedIn -> SendInviteResult.NOT_SIGNED_IN
            CallResult.NoNetwork -> SendInviteResult.NO_NETWORK
            CallResult.Failed -> SendInviteResult.FAILED
        }

    private fun Map<*, *>.friend(): Friend? {
        val f = this["friend"] as? Map<*, *> ?: return null
        val uid = f.string("uid") ?: return null
        val username = f.string("username") ?: return null
        return Friend(uid, username, f.string("displayName") ?: username)
    }
}
