package com.doomscrollduel.domain.social

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** How well this phone is connected to one other person in the room. */
enum class PeerState { CONNECTING, CONNECTED, FAILED }

/** A live voice room (a "broadcast") started by me or one of my friends. */
data class BroadcastRoomInfo(
    val id: String,
    val hostUid: String,
    val hostName: String,
    val title: String,
    val seenAtMs: Long,
    /** How many people are in it right now, and the first few of their names. */
    val count: Int = 0,
    val names: List<String> = emptyList(),
)

/** Somebody asked me to come into their room. */
data class BroadcastInvite(val fromUid: String, val fromName: String, val roomId: String, val title: String, val createdAtMs: Long)

enum class RoomInviteResult { SENT, TOO_SOON, NOT_IN_ROOM, NO_NETWORK, FAILED }

data class BroadcastMember(
    val uid: String,
    val name: String,
    val username: String,
    val isMe: Boolean,
    val isHost: Boolean,
    val state: PeerState,
    /** Talking right now (loud enough to be speech, not muted). Drives the pulse around the profile circle. */
    val speaking: Boolean = false,
    /** My audio is really leaving for this person / theirs is really arriving (packets are flowing). */
    val sending: Boolean = false,
    val receiving: Boolean = false,
)

/** One chat line. Chat lives only on the phone it was sent or received on; it is never stored on a server. */
data class ChatMessage(val id: Long, val fromUid: String, val fromName: String, val text: String, val atMs: Long, val mine: Boolean)

enum class BroadcastStatus { IDLE, JOINING, LIVE }

enum class BroadcastProblem { NO_PROFILE, NO_MIC, NOT_ALLOWED, NO_ROOM, NO_NETWORK, FAILED, REMOVED, ROOM_CLOSED }

data class BroadcastState(
    val status: BroadcastStatus = BroadcastStatus.IDLE,
    val roomId: String? = null,
    val title: String = "",
    val members: List<BroadcastMember> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val muted: Boolean = false,
    val speaker: Boolean = true,
    /** Why the last start or join did not work, or why the person was taken out of the room. Null when all is well. */
    val problem: BroadcastProblem? = null,
)

/**
 * Voice rooms between friends. Audio and chat go straight from phone to phone (WebRTC); Firestore only holds who is in the
 * room and the short-lived connection messages that let phones find each other. Chat is never stored anywhere but the
 * phones that took part.
 */
interface BroadcastRepository {
    /** Every live room, whoever started it. Anybody signed in can join any of them. */
    val rooms: Flow<List<BroadcastRoomInfo>>

    /** Invitations to rooms that are waiting for me. */
    val invites: Flow<List<BroadcastInvite>>

    val state: StateFlow<BroadcastState>

    /** Opens a new room and joins it. The microphone permission must already be granted. */
    suspend fun start(title: String)

    suspend fun join(roomId: String)

    suspend fun leave()

    fun setMuted(muted: Boolean)

    fun setSpeaker(on: Boolean)

    fun sendChat(text: String)

    fun clearProblem()

    /** Asks somebody to come into the room I am in. */
    suspend fun inviteToRoom(personUid: String): RoomInviteResult

    suspend fun dismissInvite(fromUid: String)

    /** The host removes a person from the room; they cannot join this room again. */
    suspend fun kick(uid: String)
}
