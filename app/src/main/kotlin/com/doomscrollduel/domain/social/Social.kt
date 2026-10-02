package com.doomscrollduel.domain.social

import com.doomscrollduel.domain.model.Username
import kotlinx.coroutines.flow.Flow

/** The signed-in person. [username] is what friends search for; [displayName] is what they see. */
data class Profile(val uid: String, val username: String, val displayName: String)

/** Where the person is in "sign in, then choose a username". */
sealed interface ProfileState {
    /** Not known yet (starting up, or offline with nothing saved). Screens must not push the person around yet. */
    data object Loading : ProfileState
    data object SignedOut : ProfileState
    data object NeedsUsername : ProfileState

    /** Signed in, but the server could not be reached or is not set up yet (for example Firestore is off). */
    data object Unavailable : ProfileState
    data class Ready(val profile: Profile) : ProfileState
}

enum class UsernameAvailability { AVAILABLE, TAKEN, INVALID, NO_NETWORK, NOT_SIGNED_IN, FAILED }

sealed interface ClaimResult {
    data class Created(val profile: Profile) : ClaimResult
    data object Taken : ClaimResult
    data object Invalid : ClaimResult
    data object AlreadyHasProfile : ClaimResult
    data object NoNetwork : ClaimResult
    data object NotSignedIn : ClaimResult
    data object Failed : ClaimResult
}

interface ProfileRepository {
    val state: Flow<ProfileState>

    suspend fun checkUsername(name: Username): UsernameAvailability

    /** Changes the name friends see (1 to 30 letters). The username itself never changes. Returns false when it could not be saved. */
    suspend fun updateDisplayName(name: String): Boolean

    /** Claims the username on the server (one person per name) and creates the profile. */
    suspend fun claimUsername(name: Username, displayName: String): ClaimResult
}

/** A contact who already has the app. */
data class ContactMatch(val uid: String, val username: String, val displayName: String, val isFriend: Boolean)

sealed interface ContactsResult {
    data class Found(val matches: List<ContactMatch>) : ContactsResult
    data object NoPermission : ContactsResult
    data object NoNetwork : ContactsResult
    data object NotSignedIn : ContactsResult
    data object Failed : ContactsResult
}

/**
 * Finds which contacts already use the app. Only SHA-256 hashes of contact email addresses leave the phone; the names,
 * numbers and addresses themselves never do. The person must allow the Contacts permission first.
 */
interface ContactsRepository {
    fun hasPermission(): Boolean

    suspend fun findFriends(): ContactsResult
}
