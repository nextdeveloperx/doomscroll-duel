package com.doomscrollduel.domain.social

import kotlinx.coroutines.flow.Flow

/**
 * Someone in the public directory: everybody who signed in and chose a username. Only the username and the display name
 * are public here (never an email, a hash or a reel count).
 */
data class Person(
    val uid: String,
    val username: String,
    val displayName: String,
    val isFriend: Boolean,
    /** An invite from me is already waiting for this person (sent in the last 24 hours). */
    val invited: Boolean,
)

/** An invite someone sent me that I have not answered yet. */
data class IncomingInvite(val fromUid: String, val fromUsername: String, val fromName: String, val createdAtMs: Long)

enum class PersonInviteResult { SENT, ALREADY_SENT, ALREADY_FRIENDS, NO_PROFILE, NO_NETWORK, NOT_SIGNED_IN, FAILED }

sealed interface AnswerResult {
    data class Accepted(val friendName: String) : AnswerResult
    data object Declined : AnswerResult

    /** The invite is gone (the sender's profile changed or it was already answered). */
    data object Gone : AnswerResult
    data object NoNetwork : AnswerResult
    data object Failed : AnswerResult
}

/**
 * The People list and the invites inbox. Works straight against Firestore under the security rules, so it needs no Cloud
 * Function: the rules only let a person invite somebody else once, answer only their own inbox and create friend edges
 * only for an invite that is really waiting.
 */
interface PeopleRepository {
    /** Everybody except me, by username. Empty while signed out. */
    val people: Flow<List<Person>>

    /** Invites waiting for me, newest first. */
    val incoming: Flow<List<IncomingInvite>>

    suspend fun invite(personUid: String): PersonInviteResult

    /**
     * Makes sure I am in the People list and findable by my email hash (for other people's Contacts search). Safe to call
     * any number of times; does nothing until I have chosen a username.
     */
    suspend fun ensureSelfListed()

    suspend fun accept(invite: IncomingInvite): AnswerResult

    suspend fun decline(invite: IncomingInvite): AnswerResult
}
