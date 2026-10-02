package com.doomscrollduel.data.social

import android.util.Log
import com.doomscrollduel.domain.model.EmailHash
import com.doomscrollduel.domain.repository.AuthRepository
import com.doomscrollduel.domain.social.AnswerResult
import com.doomscrollduel.domain.social.IncomingInvite
import com.doomscrollduel.domain.social.PeopleRepository
import com.doomscrollduel.domain.social.Person
import com.doomscrollduel.domain.social.PersonInviteResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await

/** See [PeopleRepository]. Collections: `directory/{uid}`, `users/{me}/inbox/{fromUid}`, `users/{me}/sentInvites/{uid}`. */
@Singleton
class FirebasePeopleRepository @Inject constructor(
    auth: AuthRepository,
    private val seen: FriendSeen,
) : PeopleRepository {

    private val db get() = FirebaseFirestore.getInstance()

    @OptIn(ExperimentalCoroutinesApi::class)
    override val people: Flow<List<Person>> = auth.session.flatMapLatest { session ->
        if (session == null) flowOf(emptyList()) else peopleOf(session.uid)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val incoming: Flow<List<IncomingInvite>> = auth.session.flatMapLatest { session ->
        if (session == null) flowOf(emptyList()) else inboxOf(session.uid)
    }

    private fun peopleOf(me: String): Flow<List<Person>> {
        val directory = snapshots(db.collection("directory").orderBy("username").limit(DIRECTORY_LIMIT.toLong()))
        val friends = snapshots(db.collection("users").document(me).collection("friends"))
        val sent = snapshots(db.collection("users").document(me).collection("sentInvites"))
        return combine(directory, friends, sent) { dir, fr, se ->
            val friendIds = fr.documents.map { it.id }.toSet()
            val now = System.currentTimeMillis()
            val sentIds = se.documents.filter { doc ->
                val at = doc.getTimestamp("createdAt")?.toDate()?.time ?: now
                now - at < INVITE_WINDOW_MS
            }.map { it.id }.toSet()
            dir.documents.mapNotNull { doc -> doc.toPerson(me, friendIds, sentIds) }
        }.onStart { runCatching { ensureMyDirectoryEntry(me) } }
    }

    private fun DocumentSnapshot.toPerson(me: String, friendIds: Set<String>, sentIds: Set<String>): Person? {
        if (id == me) return null
        val username = getString("username") ?: return null
        return Person(id, username, getString("displayName") ?: username, isFriend = id in friendIds, invited = id in sentIds)
    }

    private fun inboxOf(me: String): Flow<List<IncomingInvite>> =
        snapshots(db.collection("users").document(me).collection("inbox")).let { flow ->
            kotlinx.coroutines.flow.flow {
                flow.collect { snap ->
                    emit(
                        snap.documents.mapNotNull { doc ->
                            val username = doc.getString("fromUsername") ?: return@mapNotNull null
                            // A just-written invite has no server time yet; it is shown with "now" until the server answers.
                            val at = doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
                            IncomingInvite(doc.id, username, doc.getString("fromName") ?: username, at)
                        }.sortedByDescending { it.createdAtMs },
                    )
                }
            }
        }

    /** A listener that keeps the last good answer when an error happens (rules not deployed, offline) and says nothing new. */
    private fun snapshots(query: Query): Flow<QuerySnapshot> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) { Log.w(TAG, "listener failed: ${error.code}"); return@addSnapshotListener }
            if (snapshot != null) trySend(snapshot)
        }
        awaitClose { registration.remove() }
    }

    private fun snapshots(ref: com.google.firebase.firestore.CollectionReference): Flow<QuerySnapshot> = snapshots(ref as Query)

    /**
     * Makes sure I am in the directory. A profile made before the directory existed (or by an older build) has no entry yet:
     * it is created from the profile, once. The rules accept it only when the username really is mine.
     */
    private suspend fun ensureMyDirectoryEntry(me: String): DirectoryEntry? {
        val ref = db.collection("directory").document(me)
        val existing = ref.get().await()
        if (existing.exists()) return existing.toEntry()
        val profile = db.collection("users").document(me).get().await()
        val username = profile.getString("username") ?: return null
        val displayName = profile.getString("displayName") ?: username
        ref.set(mapOf("username" to username, "displayName" to displayName, "createdAt" to FieldValue.serverTimestamp())).await()
        return DirectoryEntry(username, displayName)
    }

    private fun DocumentSnapshot.toEntry(): DirectoryEntry? {
        val username = getString("username") ?: return null
        return DirectoryEntry(username, getString("displayName") ?: username)
    }

    override suspend fun ensureSelfListed() {
        val me = FirebaseAuth.getInstance().currentUser ?: return
        runCatching {
            if (ensureMyDirectoryEntry(me.uid) == null) return
            val email = me.email?.trim().orEmpty()
            if (email.isEmpty()) return
            val ref = db.collection("emailIndex").document(EmailHash.of(email))
            if (!ref.get().await().exists()) {
                ref.set(mapOf("uid" to me.uid, "createdAt" to FieldValue.serverTimestamp())).await()
            }
        }.onFailure { Log.w(TAG, "listing myself failed", it) }
    }

    override suspend fun invite(personUid: String): PersonInviteResult {
        val me = FirebaseAuth.getInstance().currentUser?.uid ?: return PersonInviteResult.NOT_SIGNED_IN
        if (personUid == me) return PersonInviteResult.FAILED
        return try {
            val mine = ensureMyDirectoryEntry(me) ?: return PersonInviteResult.NO_PROFILE
            val friend = db.collection("users").document(me).collection("friends").document(personUid).get().await()
            if (friend.exists()) return PersonInviteResult.ALREADY_FRIENDS
            val batch = db.batch()
            batch.set(
                db.collection("users").document(personUid).collection("inbox").document(me),
                mapOf(
                    "fromUid" to me,
                    "fromUsername" to mine.username,
                    "fromName" to mine.displayName,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
            batch.set(
                db.collection("users").document(me).collection("sentInvites").document(personUid),
                mapOf("createdAt" to FieldValue.serverTimestamp()),
            )
            batch.commit().await()
            PersonInviteResult.SENT
        } catch (e: FirebaseFirestoreException) {
            Log.w(TAG, "invite failed: ${e.code}", e)
            when (e.code) {
                // The one allowed invite for this person is already in their inbox, or the rules are not deployed.
                Code.PERMISSION_DENIED -> markSent(me, personUid)
                Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED -> PersonInviteResult.NO_NETWORK
                else -> PersonInviteResult.FAILED
            }
        } catch (e: IOException) {
            PersonInviteResult.NO_NETWORK
        }
    }

    private suspend fun markSent(me: String, personUid: String): PersonInviteResult = try {
        db.collection("users").document(me).collection("sentInvites").document(personUid)
            .set(mapOf("createdAt" to FieldValue.serverTimestamp())).await()
        PersonInviteResult.ALREADY_SENT
    } catch (e: FirebaseFirestoreException) {
        PersonInviteResult.FAILED
    }

    override suspend fun accept(invite: IncomingInvite): AnswerResult {
        val me = FirebaseAuth.getInstance().currentUser?.uid ?: return AnswerResult.Failed
        return try {
            val mine = ensureMyDirectoryEntry(me) ?: return AnswerResult.Failed
            val theirs = db.collection("directory").document(invite.fromUid).get().await().toEntry() ?: return AnswerResult.Gone
            // This phone must not announce its own friendship to itself.
            seen.add(invite.fromUid)
            val batch = db.batch()
            batch.set(
                db.collection("users").document(me).collection("friends").document(invite.fromUid),
                mapOf("uid" to invite.fromUid, "username" to theirs.username, "displayName" to theirs.displayName, "since" to FieldValue.serverTimestamp()),
            )
            batch.set(
                db.collection("users").document(invite.fromUid).collection("friends").document(me),
                mapOf("uid" to me, "username" to mine.username, "displayName" to mine.displayName, "since" to FieldValue.serverTimestamp()),
            )
            batch.delete(db.collection("users").document(me).collection("inbox").document(invite.fromUid))
            batch.delete(db.collection("users").document(me).collection("sentInvites").document(invite.fromUid))
            batch.commit().await()
            AnswerResult.Accepted(theirs.displayName)
        } catch (e: FirebaseFirestoreException) {
            Log.w(TAG, "accept failed: ${e.code}", e)
            when (e.code) {
                Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED -> AnswerResult.NoNetwork
                Code.PERMISSION_DENIED, Code.NOT_FOUND -> AnswerResult.Gone
                else -> AnswerResult.Failed
            }
        } catch (e: IOException) {
            AnswerResult.NoNetwork
        }
    }

    override suspend fun decline(invite: IncomingInvite): AnswerResult {
        val me = FirebaseAuth.getInstance().currentUser?.uid ?: return AnswerResult.Failed
        return try {
            db.collection("users").document(me).collection("inbox").document(invite.fromUid).delete().await()
            AnswerResult.Declined
        } catch (e: FirebaseFirestoreException) {
            if (e.code == Code.UNAVAILABLE) AnswerResult.NoNetwork else AnswerResult.Failed
        } catch (e: IOException) {
            AnswerResult.NoNetwork
        }
    }

    private data class DirectoryEntry(val username: String, val displayName: String)

    private companion object {
        const val TAG = "PeopleRepository"
        const val DIRECTORY_LIMIT = 500
        const val INVITE_WINDOW_MS = 24L * 60 * 60 * 1000
    }
}
