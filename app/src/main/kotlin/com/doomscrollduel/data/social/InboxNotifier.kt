package com.doomscrollduel.data.social

import android.content.Context
import android.util.Log
import com.doomscrollduel.domain.repository.AuthRepository
import com.doomscrollduel.feature.friends.FriendNotifications
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Friends this phone already knows about, so a new one is announced once and never for one this person just accepted. */
@Singleton
class FriendSeen @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("friend_seen", Context.MODE_PRIVATE)

    fun add(uid: String) {
        prefs.edit().putStringSet(KEY, (prefs.getStringSet(KEY, emptySet()).orEmpty()) + uid).apply()
    }

    fun contains(uid: String): Boolean = uid in prefs.getStringSet(KEY, emptySet()).orEmpty()

    /** True once the first full list of friends has been read on this phone. Before that nothing is announced. */
    var primed: Boolean
        get() = prefs.getBoolean(PRIMED, false)
        set(value) { prefs.edit().putBoolean(PRIMED, value).apply() }

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY = "uids"
        const val PRIMED = "primed"
    }
}

/**
 * Raises the phone notification for a new invite (and for a friend who accepted mine) while the app process is alive,
 * which is whenever the reel counter's foreground service runs. When the Cloud Function `onInviteInboxCreated` is
 * deployed, a push covers the closed-app case too; the notification id is the same, so nobody sees two.
 *
 * Only a first name and a username are in the text; nothing about reels.
 */
@Singleton
class InboxNotifier @Inject constructor(
    private val auth: AuthRepository,
    @ApplicationContext private val context: Context,
    private val seen: FriendSeen,
    private val profiles: com.doomscrollduel.domain.social.ProfileRepository,
    private val people: com.doomscrollduel.domain.social.PeopleRepository,
) {
    private val prefs = context.getSharedPreferences("inbox_notified", Context.MODE_PRIVATE)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            auth.session.collectLatest { session ->
                if (session == null) {
                    prefs.edit().clear().apply()
                    seen.clear()
                } else {
                    val uid = session.uid
                    launch { watchInbox(uid) }
                    launch { watchBroadcastInvites(uid) }
                    // Once the username exists, put this person in the People list and the contacts index.
                    launch {
                        profiles.state.first { it is com.doomscrollduel.domain.social.ProfileState.Ready }
                        people.ensureSelfListed()
                    }
                    watchFriends(uid)
                }
            }
        }
    }

    private suspend fun watchInbox(uid: String) {
        listen(FirebaseFirestore.getInstance().collection("users").document(uid).collection("inbox")).collect { docs ->
            val done = prefs.getStringSet(KEY, emptySet()).orEmpty().toMutableSet()
            docs.forEach { doc ->
                val at = doc.getTimestamp("createdAt")?.toDate()?.time ?: return@forEach // still being written
                val key = "${doc.id}:$at"
                if (key in done) return@forEach
                done += key
                FriendNotifications.showInviteFromPerson(context, doc.id, doc.getString("fromName") ?: doc.getString("fromUsername").orEmpty())
            }
            prefs.edit().putStringSet(KEY, done.toList().takeLast(MAX_REMEMBERED).toSet()).apply()
        }
    }

    /** A phone notification for each new "come to my broadcast"; once per invitation. */
    private suspend fun watchBroadcastInvites(uid: String) {
        listen(FirebaseFirestore.getInstance().collection("users").document(uid).collection("broadcastInvites")).collect { docs ->
            val done = prefs.getStringSet(KEY, emptySet()).orEmpty().toMutableSet()
            val now = System.currentTimeMillis()
            docs.forEach { doc ->
                val at = doc.getTimestamp("createdAt")?.toDate()?.time ?: return@forEach
                val roomId = doc.getString("roomId") ?: return@forEach
                val key = "bc:${doc.id}:$at"
                if (key in done || now - at > 10 * 60_000L) return@forEach
                done += key
                FriendNotifications.showBroadcastInvite(context, roomId, doc.getString("fromName").orEmpty(), doc.getString("title").orEmpty())
            }
            prefs.edit().putStringSet(KEY, done.toList().takeLast(MAX_REMEMBERED).toSet()).apply()
        }
    }

    private suspend fun watchFriends(uid: String) {
        listen(FirebaseFirestore.getInstance().collection("users").document(uid).collection("friends")).collect { docs ->
            val first = !seen.primed
            docs.forEach { doc ->
                if (seen.contains(doc.id)) return@forEach
                seen.add(doc.id)
                if (!first) FriendNotifications.showJoined(context, doc.getString("displayName") ?: doc.getString("username").orEmpty())
            }
            seen.primed = true
        }
    }

    private fun listen(ref: com.google.firebase.firestore.CollectionReference): Flow<List<com.google.firebase.firestore.DocumentSnapshot>> = callbackFlow {
        val registration = ref.addSnapshotListener { snapshot, error ->
            if (error != null) { Log.w(TAG, "inbox listener: ${error.code}"); return@addSnapshotListener }
            // Answers from the local cache can lag behind; only trust ones the server has confirmed or that are local writes.
            if (snapshot != null) trySend(snapshot.documents)
        }
        awaitClose { registration.remove() }
    }

    private companion object {
        const val TAG = "InboxNotifier"
        const val KEY = "keys"
        const val MAX_REMEMBERED = 200
    }
}
