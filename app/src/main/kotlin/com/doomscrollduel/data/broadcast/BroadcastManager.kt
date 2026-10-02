package com.doomscrollduel.data.broadcast

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.util.Log
import com.doomscrollduel.domain.repository.AuthRepository
import com.doomscrollduel.domain.social.BroadcastInvite
import com.doomscrollduel.domain.social.BroadcastMember
import com.doomscrollduel.domain.social.RoomInviteResult
import com.doomscrollduel.domain.social.BroadcastProblem
import com.doomscrollduel.domain.social.BroadcastRepository
import com.doomscrollduel.domain.social.BroadcastRoomInfo
import com.doomscrollduel.domain.social.BroadcastState
import com.doomscrollduel.domain.social.BroadcastStatus
import com.doomscrollduel.domain.social.ChatMessage
import com.doomscrollduel.domain.social.PeerState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs the voice rooms: the room list, joining and leaving, who is in the room, the connection messages that let phones
 * find each other, the microphone and speaker switches, and the chat. See [BroadcastRepository] for what is stored where.
 * Firestore holds `broadcasts/{room}` (+ `members`, + short-lived `signals`); audio and chat never touch it.
 */
@Singleton
class BroadcastManager @Inject constructor(
    private val auth: AuthRepository,
    private val chatStore: BroadcastChatStore,
    @ApplicationContext private val context: Context,
) : BroadcastRepository {

    private val db get() = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private val nextId = AtomicLong(System.currentTimeMillis())

    private val _state = MutableStateFlow(BroadcastState())
    override val state: StateFlow<BroadcastState> = _state

    private class Session(
        val roomId: String,
        val hostUid: String,
        val me: String,
        val myName: String,
        val mesh: VoiceMesh,
        val job: Job,
        val members: MutableStateFlow<List<MemberDoc>>,
    )

    private data class MemberDoc(val uid: String, val name: String, val username: String, val joinedAtMs: Long, val seenAtMs: Long)

    private var session: Session? = null

    /** My name and username, kept after the first lookup so joining a room does not wait for the directory again. */
    @Volatile private var cachedProfile: Triple<String, String, String>? = null

    private suspend fun myProfile(uid: String): Pair<String, String>? {
        cachedProfile?.takeIf { it.first == uid }?.let { return it.second to it.third }
        return profileOf(uid)?.also { cachedProfile = Triple(uid, it.first, it.second) }
    }

    // ----- the room list --------------------------------------------------------------------------------------------------

    @OptIn(ExperimentalCoroutinesApi::class)
    override val rooms: Flow<List<BroadcastRoomInfo>> = auth.session.flatMapLatest { s ->
        if (s == null) {
            flowOf(emptyList())
        } else {
            scope.launch { myProfile(s.uid) } // warm the cache while the list is on screen, so Join is quick
            combine(roomDocs(), ticker(15_000L)) { docs, now -> docs.filter { now - it.seenAtMs < FRESH_MS } }
                .mapLatest { docs -> withPeople(docs.take(MAX_ROOMS)) }
        }
    }

    /** Fills in how many people are in each room and who the first few are; rooms nobody is in any more are left out. */
    private suspend fun withPeople(rooms: List<BroadcastRoomInfo>): List<BroadcastRoomInfo> = coroutineScope {
        rooms.map { room ->
            async {
                val now = System.currentTimeMillis()
                val fresh = runCatching { db.collection("broadcasts").document(room.id).collection("members").get().await().documents }
                    .getOrDefault(emptyList())
                    .filter { now - (it.getTimestamp("seenAt")?.toDate()?.time ?: now) < FRESH_MS }
                room.copy(count = fresh.size, names = fresh.mapNotNull { it.getString("name") }.take(3))
            }
        }.awaitAll().filter { it.count > 0 }
    }

    override val invites: Flow<List<BroadcastInvite>> = auth.session.flatMapLatest { s ->
        if (s == null) flowOf(emptyList()) else invitesOf(s.uid)
    }

    private fun invitesOf(me: String): Flow<List<BroadcastInvite>> = callbackFlow {
        val registration = db.collection("users").document(me).collection("broadcastInvites").addSnapshotListener { snapshot, error ->
            if (error != null) { Log.w(TAG, "room invites listener: ${error.code}"); return@addSnapshotListener }
            if (snapshot == null) return@addSnapshotListener
            val now = System.currentTimeMillis()
            trySend(
                snapshot.documents.mapNotNull { doc ->
                    val at = doc.getTimestamp("createdAt")?.toDate()?.time ?: now
                    // An invitation older than an hour is stale: the room has almost certainly ended.
                    if (now - at > INVITE_TTL_MS) return@mapNotNull null
                    BroadcastInvite(doc.id, doc.getString("fromName") ?: return@mapNotNull null, doc.getString("roomId") ?: return@mapNotNull null, doc.getString("title").orEmpty(), at)
                }.sortedByDescending { it.createdAtMs },
            )
        }
        awaitClose { registration.remove() }
    }

    override suspend fun inviteToRoom(personUid: String): RoomInviteResult {
        val s = session ?: return RoomInviteResult.NOT_IN_ROOM
        val title = _state.value.title
        return try {
            withTimeout(NETWORK_MS) {
                db.collection("users").document(personUid).collection("broadcastInvites").document(s.me).set(
                    mapOf(
                        "fromUid" to s.me,
                        "fromName" to s.myName,
                        "roomId" to s.roomId,
                        "title" to title,
                        "createdAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            }
            RoomInviteResult.SENT
        } catch (e: FirebaseFirestoreException) {
            Log.w(TAG, "room invite failed: ${e.code}")
            when (e.code) {
                // One invitation per person every 30 seconds; the rules refuse a faster second one.
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> RoomInviteResult.TOO_SOON
                FirebaseFirestoreException.Code.UNAVAILABLE -> RoomInviteResult.NO_NETWORK
                else -> RoomInviteResult.FAILED
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.TimeoutCancellationException) RoomInviteResult.NO_NETWORK else RoomInviteResult.FAILED
        }
    }

    override suspend fun dismissInvite(fromUid: String) {
        val me = FirebaseAuth.getInstance().currentUser?.uid ?: return
        withTimeoutOrNull(NETWORK_MS) {
            runCatching { db.collection("users").document(me).collection("broadcastInvites").document(fromUid).delete().await() }
        }
    }

    override suspend fun kick(uid: String) {
        val s = session ?: return
        if (s.hostUid != s.me || uid == s.me) return
        withTimeoutOrNull(NETWORK_MS) {
            runCatching {
                val room = db.collection("broadcasts").document(s.roomId)
                // First the ban (so they cannot walk back in), then take them out of the room.
                room.collection("kicked").document(uid).set(mapOf("uid" to uid, "createdAt" to FieldValue.serverTimestamp())).await()
                room.collection("members").document(uid).delete().await()
            }
        }
    }

    private fun roomDocs(): Flow<List<BroadcastRoomInfo>> = callbackFlow {
        val registration = db.collection("broadcasts").orderBy("seenAt", Query.Direction.DESCENDING).limit(40)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { Log.w(TAG, "rooms listener: ${error.code}"); return@addSnapshotListener }
                if (snapshot == null) return@addSnapshotListener
                trySend(
                    snapshot.documents.mapNotNull { doc ->
                        val host = doc.getString("hostUid") ?: return@mapNotNull null
                        if (doc.getBoolean("closed") == true) return@mapNotNull null
                        BroadcastRoomInfo(
                            id = doc.id,
                            hostUid = host,
                            hostName = doc.getString("hostName") ?: "?",
                            title = doc.getString("title") ?: "",
                            seenAtMs = doc.getTimestamp("seenAt")?.toDate()?.time ?: System.currentTimeMillis(),
                        )
                    },
                )
            }
        awaitClose { registration.remove() }
    }

    private fun ticker(everyMs: Long): Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(everyMs)
        }
    }

    // ----- start / join / leave -------------------------------------------------------------------------------------------

    override suspend fun start(title: String) {
        val me = FirebaseAuth.getInstance().currentUser?.uid ?: return fail(BroadcastProblem.FAILED)
        val name = myProfile(me)?.first ?: return fail(BroadcastProblem.NO_PROFILE)
        // Rooms I left behind (the app was closed without leaving) would show as live for a minute: remove them first.
        runCatching {
            withTimeout(NETWORK_MS) {
                db.collection("broadcasts").whereEqualTo("hostUid", me).get().await().documents.forEach { it.reference.delete() }
            }
        }
        val ref = db.collection("broadcasts").document()
        try {
            withTimeout(NETWORK_MS) {
                ref.set(
                    mapOf(
                        "hostUid" to me,
                        "hostName" to name,
                        "title" to title.trim().ifBlank { name }.take(40),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "seenAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            }
        } catch (e: Exception) {
            return fail(problemOf(e))
        }
        join(ref.id)
    }

    override suspend fun join(roomId: String) = lock.withLock { joinLocked(roomId) }

    private suspend fun joinLocked(roomId: String) {
        if (session?.roomId == roomId) return
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return fail(BroadcastProblem.NO_MIC)
        val me = FirebaseAuth.getInstance().currentUser?.uid ?: return fail(BroadcastProblem.FAILED)
        val startedAt = System.currentTimeMillis()
        // "Joining" shows at once; the rest takes a moment, so it all runs side by side instead of one thing after the other.
        _state.value = BroadcastState(status = BroadcastStatus.JOINING, roomId = roomId)
        leaveLocked(resetState = false)

        val members = MutableStateFlow<List<MemberDoc>>(emptyList())
        val peerStates = MutableStateFlow<Map<String, PeerState>>(emptyMap())
        val speaking = MutableStateFlow<Set<String>>(emptySet())
        val flows = MutableStateFlow<Map<String, Pair<Boolean, Boolean>>>(emptyMap())
        val roomSeen = MutableStateFlow(0L)

        // The voice engine takes the longest to start, so it starts first and the network steps run while it does.
        val meshStart = scope.async {
            runCatching {
                VoiceMesh(
                    context = context,
                    scope = scope,
                    me = me,
                    sendSignal = { to, signal -> sendSignal(roomId, me, to, signal) },
                    onPeerState = { uid, state -> peerStates.update { it + (uid to state) } },
                    onChat = { from, name, text -> receiveChat(from, name, text) },
                    onSpeaking = { uid, on -> speaking.update { if (on) it + uid else it - uid } },
                    onFlow = { uid, sending, receiving -> flows.update { it + (uid to (sending to receiving)) } },
                )
            }
        }
        suspend fun dropMesh() {
            meshStart.await().getOrNull()?.close()
        }

        val roomRef = db.collection("broadcasts").document(roomId)
        var myName = ""
        var title = ""
        var hostUid = ""
        try {
            coroutineScope {
                val profileJob = async { myProfile(me) }
                val roomJob = async { withTimeout(NETWORK_MS) { roomRef.get().await() } }
                // Connection messages left over from an earlier visit must be gone BEFORE I appear in the room: the moment I do,
                // the others start sending me offers, and clearing afterwards could delete one of those.
                val cleanJob = async {
                    runCatching {
                        withTimeout(NETWORK_MS) { roomRef.collection("signals").whereEqualTo("to", me).get().await().documents.forEach { it.reference.delete() } }
                    }
                }
                val profile = profileJob.await() ?: throw NoProfile()
                val room = roomJob.await()
                if (!room.exists() || room.getBoolean("closed") == true) throw MissingRoom()
                title = room.getString("title").orEmpty()
                hostUid = room.getString("hostUid").orEmpty()
                myName = profile.first
                cleanJob.await()
                withTimeout(NETWORK_MS) {
                    roomRef.collection("members").document(me).set(
                        mapOf(
                            "uid" to me,
                            "name" to profile.first,
                            "username" to profile.second,
                            "joinedAt" to FieldValue.serverTimestamp(),
                            "seenAt" to FieldValue.serverTimestamp(),
                        ),
                    ).await()
                }
            }
        } catch (e: Exception) {
            dropMesh()
            return fail(if (e is NoProfile) BroadcastProblem.NO_PROFILE else problemOf(e))
        }
        val mesh = meshStart.await().getOrElse {
            Log.w(TAG, "voice engine failed to start", it)
            runCatching { roomRef.collection("members").document(me).delete() }
            return fail(BroadcastProblem.FAILED)
        }
        setAudioMode(true)
        val job = scope.launch {
            launch { collectMembers(roomId).collect { members.value = it } }
            launch { collectSignals(roomId, me).collect { list -> list.forEach { handleSignal(mesh, it) } } }
            launch { collectRoom(roomId).collect { room -> onRoomChanged(roomId, hostUid, me, room, roomSeen) } }
            launch { heartbeat(roomId, me) }
            launch { follow(members, peerStates, speaking, flows, roomSeen, me, hostUid, roomId, mesh) }
        }
        session = Session(roomId, hostUid, me, myName, mesh, job, members)
        BroadcastService.start(context, title)
        Log.i(TAG, "joined the room in ${System.currentTimeMillis() - startedAt} ms")
        _state.value = BroadcastState(
            status = BroadcastStatus.LIVE,
            roomId = roomId,
            title = title,
            messages = chatStore.load(roomId),
        )
    }

    override suspend fun leave() = lock.withLock { leaveLocked() }

    private suspend fun leaveLocked(problem: BroadcastProblem? = null, resetState: Boolean = true) {
        val s = session
        session = null
        if (s != null) {
            s.job.cancel()
            s.mesh.close()
            val room = db.collection("broadcasts").document(s.roomId)
            if (s.hostUid == s.me) {
                // The host leaving closes the room for everybody: a `closed` mark every phone in it sees at once. The room and its
                // member list are tidied away a little later, once they have all had time to see the mark.
                withTimeoutOrNull(LEAVE_MS) { runCatching { room.update("closed", true).await() } }
                scope.launch { delay(CLOSE_CLEANUP_MS); removeRoom(s.roomId) }
            } else {
                withTimeoutOrNull(LEAVE_MS) { runCatching { room.collection("members").document(s.me).delete().await() } }
            }
            BroadcastService.stop(context)
            setAudioMode(false)
        }
        if (resetState) _state.value = BroadcastState(problem = problem)
    }

    /** Host only: deletes the member list (while the room still names me host, which the rules need) and then the room. */
    private suspend fun removeRoom(roomId: String) {
        withTimeoutOrNull(NETWORK_MS) {
            runCatching {
                val room = db.collection("broadcasts").document(roomId)
                room.collection("members").get().await().documents.forEach { it.reference.delete() }
                room.delete().await()
            }
        }
    }

    private class RoomDoc(val exists: Boolean, val closed: Boolean, val seenAtMs: Long)

    private fun collectRoom(roomId: String): Flow<RoomDoc> = callbackFlow {
        val registration = db.collection("broadcasts").document(roomId).addSnapshotListener { snapshot, error ->
            if (error != null) { Log.w(TAG, "room listener: ${error.code}"); return@addSnapshotListener }
            if (snapshot == null) return@addSnapshotListener
            // A first answer from the phone's own cache can say "not there" before the server has been asked: not proof of anything.
            if (!snapshot.exists() && snapshot.metadata.isFromCache) return@addSnapshotListener
            trySend(RoomDoc(snapshot.exists(), snapshot.getBoolean("closed") == true, snapshot.getTimestamp("seenAt")?.toDate()?.time ?: 0L))
        }
        awaitClose { registration.remove() }
    }

    /** The room was closed or deleted by the host: everybody else leaves with a message. */
    private fun onRoomChanged(roomId: String, hostUid: String, me: String, room: RoomDoc, roomSeen: MutableStateFlow<Long>) {
        if (room.seenAtMs > 0) roomSeen.value = room.seenAtMs
        if ((!room.exists || room.closed) && hostUid != me) {
            scope.launch { lock.withLock { if (session?.roomId == roomId) leaveLocked(BroadcastProblem.ROOM_CLOSED) } }
        }
    }

    // ----- following the room ---------------------------------------------------------------------------------------------

    private suspend fun follow(
        members: StateFlow<List<MemberDoc>>,
        peerStates: StateFlow<Map<String, PeerState>>,
        speaking: StateFlow<Set<String>>,
        flows: StateFlow<Map<String, Pair<Boolean, Boolean>>>,
        roomSeen: StateFlow<Long>,
        me: String,
        hostUid: String,
        roomId: String,
        mesh: VoiceMesh,
    ) {
        var seenMyself = false
        combine(members, peerStates, speaking, flows, ticker(10_000L)) { raw, peers, talking, flow, now -> Five(raw, peers, talking, flow, now) }.collect { (raw, peers, talking, flow, now) ->
            // The host's phone died without closing the room: its sign of life stopped. Nobody should sit in an empty room.
            val hostSeen = roomSeen.value
            if (hostUid != me && hostSeen > 0 && now - hostSeen > HOST_GONE_MS) {
                scope.launch { lock.withLock { if (session?.roomId == roomId) leaveLocked(BroadcastProblem.ROOM_CLOSED) } }
                return@collect
            }
            if (raw.any { it.uid == me }) {
                seenMyself = true
            } else if (seenMyself) {
                // My member document is gone: the host removed me.
                scope.launch { lock.withLock { leaveLocked(BroadcastProblem.REMOVED) } }
                return@collect
            }
            val fresh = raw.filter { it.uid == me || now - it.seenAtMs < FRESH_MS }.sortedBy { it.joinedAtMs }
            _state.update { current ->
                if (current.status == BroadcastStatus.IDLE) current else current.copy(
                    members = fresh.map { m ->
                        BroadcastMember(
                            uid = m.uid, name = m.name, username = m.username,
                            isMe = m.uid == me, isHost = m.uid == hostUid,
                            state = if (m.uid == me) PeerState.CONNECTED else peers[m.uid] ?: PeerState.CONNECTING,
                            speaking = m.uid in talking,
                            sending = flow[m.uid]?.first == true,
                            receiving = flow[m.uid]?.second == true,
                        )
                    },
                )
            }
            mesh.reconcile(fresh.map { it.uid }.toSet() - me)
        }
    }

    private fun collectMembers(roomId: String): Flow<List<MemberDoc>> = callbackFlow {
        val registration = db.collection("broadcasts").document(roomId).collection("members").addSnapshotListener { snapshot, error ->
            if (error != null) { Log.w(TAG, "members listener: ${error.code}"); return@addSnapshotListener }
            if (snapshot == null) return@addSnapshotListener
            trySend(
                snapshot.documents.mapNotNull { doc ->
                    MemberDoc(
                        uid = doc.id,
                        name = doc.getString("name") ?: return@mapNotNull null,
                        username = doc.getString("username").orEmpty(),
                        joinedAtMs = doc.getTimestamp("joinedAt")?.toDate()?.time ?: System.currentTimeMillis(),
                        seenAtMs = doc.getTimestamp("seenAt")?.toDate()?.time ?: System.currentTimeMillis(),
                    )
                },
            )
        }
        awaitClose { registration.remove() }
    }

    private data class SignalDoc(val id: String, val from: String, val signal: Signal, val createdAtMs: Long, val ref: com.google.firebase.firestore.DocumentReference)

    private fun collectSignals(roomId: String, me: String): Flow<List<SignalDoc>> = callbackFlow {
        val registration = db.collection("broadcasts").document(roomId).collection("signals").whereEqualTo("to", me)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { Log.w(TAG, "signals listener: ${error.code}"); return@addSnapshotListener }
                if (snapshot == null) return@addSnapshotListener
                val added = snapshot.documentChanges.filter { it.type == DocumentChange.Type.ADDED }.mapNotNull { change ->
                    val doc = change.document
                    val from = doc.getString("from") ?: return@mapNotNull null
                    val signal = when (doc.getString("type")) {
                        "offer" -> Signal.Offer(doc.getString("sdp") ?: return@mapNotNull null)
                        "answer" -> Signal.Answer(doc.getString("sdp") ?: return@mapNotNull null)
                        "ice" -> Signal.Ice(doc.getString("mid"), (doc.getLong("index") ?: 0L).toInt(), doc.getString("candidate") ?: return@mapNotNull null)
                        else -> return@mapNotNull null
                    }
                    SignalDoc(doc.id, from, signal, doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L, doc.reference)
                }
                if (added.isNotEmpty()) trySend(added.sortedBy { it.createdAtMs })
            }
        awaitClose { registration.remove() }
    }

    private suspend fun handleSignal(mesh: VoiceMesh, doc: SignalDoc) {
        mesh.onSignal(doc.from, doc.signal)
        runCatching { doc.ref.delete() }
    }

    private suspend fun sendSignal(roomId: String, me: String, to: String, signal: Signal) {
        val data = mutableMapOf<String, Any>("from" to me, "to" to to, "createdAt" to FieldValue.serverTimestamp())
        when (signal) {
            is Signal.Offer -> { data["type"] = "offer"; data["sdp"] = signal.sdp }
            is Signal.Answer -> { data["type"] = "answer"; data["sdp"] = signal.sdp }
            is Signal.Ice -> {
                data["type"] = "ice"
                signal.mid?.let { data["mid"] = it }
                data["index"] = signal.index
                data["candidate"] = signal.candidate
            }
        }
        db.collection("broadcasts").document(roomId).collection("signals").add(data).await()
    }

    private suspend fun heartbeat(roomId: String, me: String) {
        while (true) {
            delay(HEARTBEAT_MS)
            val room = db.collection("broadcasts").document(roomId)
            runCatching { room.collection("members").document(me).update("seenAt", FieldValue.serverTimestamp()).await() }
            runCatching { room.update("seenAt", FieldValue.serverTimestamp()).await() }
        }
    }

    // ----- switches and chat ----------------------------------------------------------------------------------------------

    override fun setMuted(muted: Boolean) {
        session?.mesh?.setMuted(muted)
        _state.update { it.copy(muted = muted) }
    }

    override fun setSpeaker(on: Boolean) {
        context.getSystemService(AudioManager::class.java).isSpeakerphoneOn = on
        _state.update { it.copy(speaker = on) }
    }

    override fun sendChat(text: String) {
        val clean = text.trim().take(MAX_CHAT)
        val s = session ?: return
        if (clean.isEmpty()) return
        s.mesh.sendChat(clean, s.myName)
        addMessage(ChatMessage(nextId.incrementAndGet(), s.me, s.myName, clean, System.currentTimeMillis(), mine = true))
    }

    private fun receiveChat(fromUid: String, sentName: String, text: String) {
        val s = session ?: return
        val name = _state.value.members.firstOrNull { it.uid == fromUid }?.name
            ?: s.members.value.firstOrNull { it.uid == fromUid }?.name
            ?: sentName.ifBlank { "?" }
        addMessage(ChatMessage(nextId.incrementAndGet(), fromUid, name, text, System.currentTimeMillis(), mine = false))
    }

    private fun addMessage(message: ChatMessage) {
        val roomId = session?.roomId ?: return
        _state.update { it.copy(messages = (it.messages + message).takeLast(MAX_LINES)) }
        chatStore.save(roomId, _state.value.messages)
    }

    override fun clearProblem() {
        _state.update { it.copy(problem = null) }
    }

    // ----- helpers --------------------------------------------------------------------------------------------------------

    private fun fail(problem: BroadcastProblem) {
        _state.value = BroadcastState(problem = problem)
    }

    private data class Five<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

    private class MissingRoom : Exception()

    private class NoProfile : Exception()

    private fun problemOf(e: Exception): BroadcastProblem = when {
        e is MissingRoom -> BroadcastProblem.NO_ROOM
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> BroadcastProblem.NOT_ALLOWED
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.UNAVAILABLE -> BroadcastProblem.NO_NETWORK
        e is kotlinx.coroutines.TimeoutCancellationException -> BroadcastProblem.NO_NETWORK
        else -> BroadcastProblem.FAILED
    }

    /** My display name and username, from the public directory (or my own profile if the directory has no entry yet). */
    private suspend fun profileOf(uid: String): Pair<String, String>? = try {
        withTimeout(NETWORK_MS) {
            val entry = db.collection("directory").document(uid).get().await()
            val username = entry.getString("username")
            if (username != null) {
                (entry.getString("displayName") ?: username) to username
            } else {
                val user = db.collection("users").document(uid).get().await()
                user.getString("username")?.let { (user.getString("displayName") ?: it) to it }
            }
        }
    } catch (e: Exception) {
        null
    }

    private fun setAudioMode(inCall: Boolean) {
        val audio = context.getSystemService(AudioManager::class.java)
        audio.mode = if (inCall) AudioManager.MODE_IN_COMMUNICATION else AudioManager.MODE_NORMAL
        audio.isSpeakerphoneOn = inCall
    }

    private suspend fun <T> Task<T>.awaitOrNull(): T? = try { await() } catch (e: Exception) { null }

    private companion object {
        const val TAG = "BroadcastManager"

        /** A person or room whose last sign of life is older than this is treated as gone. */
        const val FRESH_MS = 70_000L
        const val HEARTBEAT_MS = 20_000L
        const val NETWORK_MS = 12_000L
        const val LEAVE_MS = 3_000L
        const val CLOSE_CLEANUP_MS = 4_000L

        /** The host's room heartbeat is every 20 s; this long without one means the host is gone. */
        const val HOST_GONE_MS = 120_000L
        const val MAX_ROOMS = 20
        const val INVITE_TTL_MS = 60 * 60 * 1000L
        const val MAX_CHAT = 500
        const val MAX_LINES = 300
    }
}
