package com.doomscrollduel.feature.friends

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doomscrollduel.R
import com.doomscrollduel.domain.model.InviteLinks
import com.doomscrollduel.domain.model.Username
import com.doomscrollduel.domain.model.UsernameResult
import com.doomscrollduel.domain.repository.AddFriendResult
import com.doomscrollduel.domain.repository.Friend
import com.doomscrollduel.domain.repository.FriendsRepository
import com.doomscrollduel.domain.repository.SendInviteResult
import com.doomscrollduel.data.social.DuelTracker
import com.doomscrollduel.domain.social.AnswerResult
import com.doomscrollduel.domain.social.BroadcastInvite
import com.doomscrollduel.domain.social.BroadcastRepository
import com.doomscrollduel.domain.social.DuelAction
import com.doomscrollduel.domain.social.DuelInfo
import com.doomscrollduel.domain.social.DuelRepository
import com.doomscrollduel.domain.social.DuelStatus
import com.doomscrollduel.domain.social.ContactMatch
import com.doomscrollduel.domain.social.IncomingInvite
import com.doomscrollduel.domain.social.PeopleRepository
import com.doomscrollduel.domain.social.Person
import com.doomscrollduel.domain.social.PersonInviteResult
import com.doomscrollduel.domain.social.ContactsRepository
import com.doomscrollduel.domain.social.ContactsResult
import com.doomscrollduel.domain.social.Profile
import com.doomscrollduel.domain.social.ProfileRepository
import com.doomscrollduel.domain.social.ProfileState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One line of feedback ("@rohan ab tumhara dost hai"), shown for a moment. */
data class Notice(@StringRes val text: Int, val arg: String? = null, val good: Boolean = true)

sealed interface ContactsUi {
    /** Nothing asked yet: shows the explanation and the button. */
    data object NotAsked : ContactsUi
    data object Denied : ContactsUi
    data object Loading : ContactsUi
    data class Found(val matches: List<ContactMatch>) : ContactsUi
    data class Problem(val notSignedIn: Boolean, val offline: Boolean) : ContactsUi
}

enum class FriendsTab { INVITES, PEOPLE, FRIENDS }

data class FriendsUi(
    val tab: FriendsTab = FriendsTab.PEOPLE,
    val search: String = "",
    /** Everybody in the directory who matches [search], without me. */
    val people: List<Person> = emptyList(),
    val peopleTotal: Int = 0,
    val incoming: List<IncomingInvite> = emptyList(),
    val invitingUid: String? = null,
    /** Battles other people have challenged me to and I have not answered. */
    val challenges: List<DuelInfo> = emptyList(),
    val answeringDuel: String? = null,
    /** People asking me into their broadcast. */
    val broadcastInvites: List<BroadcastInvite> = emptyList(),
    val answeringUid: String? = null,
    val me: Profile? = null,
    val signedIn: Boolean = false,
    /** Signed in, but the server is not answering (not set up yet, or no internet). */
    val backendDown: Boolean = false,
    val friends: List<Friend> = emptyList(),
    val contacts: ContactsUi = ContactsUi.NotAsked,
    val addText: String = "",
    val adding: Boolean = false,
    val sharing: Boolean = false,
    /** People already sent a personal invite in this visit, so the button turns into "BHEJA". */
    val invited: Set<String> = emptySet(),
    val busyUid: String? = null,
    val notice: Notice? = null,
)

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val friendsRepo: FriendsRepository,
    private val contactsRepo: ContactsRepository,
    private val peopleRepo: PeopleRepository,
    private val duelRepo: DuelRepository,
    private val broadcastRepo: BroadcastRepository,
    private val tracker: DuelTracker,
    profiles: ProfileRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val local = MutableStateFlow(FriendsUi())
    private val shareChannel = Channel<String>(Channel.BUFFERED)

    /** Text to hand to the phone's share sheet. */
    val shareRequests = shareChannel.receiveAsFlow()

    private var tabChosen = false

    /** A battle I accepted: the page opens it. */
    private val openDuelChannel = Channel<String>(Channel.BUFFERED)
    val openDuel = openDuelChannel.receiveAsFlow()

    val ui: StateFlow<FriendsUi> = combine(
        combine(local, profiles.state, friendsRepo.friends) { state, profile, friends -> Triple(state, profile, friends) },
        combine(peopleRepo.people, peopleRepo.incoming, duelRepo.duels, broadcastRepo.invites) { people, incoming, duels, rooms -> Pair(Triple(people, incoming, duels), rooms) },
    ) { (state, profile, friends), (bundle, roomInvites) ->
        val (people, incoming, duels) = bundle
        val query = state.search.trim().lowercase()
        val me = (profile as? ProfileState.Ready)?.profile
        state.copy(
            me = me,
            signedIn = profile != ProfileState.SignedOut,
            backendDown = profile is ProfileState.Unavailable,
            friends = friends,
            people = if (query.isEmpty()) people else people.filter { query in it.username.lowercase() || query in it.displayName.lowercase() },
            peopleTotal = people.size,
            incoming = incoming,
            broadcastInvites = roomInvites,
            challenges = if (me == null) emptyList() else duels.filter { it.status == DuelStatus.PENDING && it.opponentUid == me.uid },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FriendsUi())

    init {
        // Opening the page from an invite notification (or with invites waiting) lands on the invites.
        viewModelScope.launch {
            combine(peopleRepo.incoming, duelRepo.duels, broadcastRepo.invites) { invites, duels, rooms -> invites.isNotEmpty() || rooms.isNotEmpty() || duels.any { it.status == DuelStatus.PENDING } }.collect { waiting ->
                if (waiting && !tabChosen) local.update { it.copy(tab = FriendsTab.INVITES) }
            }
        }
        // Someone who already allowed Contacts does not have to tap again.
        if (contactsRepo.hasPermission()) loadContacts()
    }

    fun onAddText(text: String) {
        local.update { it.copy(addText = text.lowercase().filter { c -> c in 'a'..'z' || c in '0'..'9' || c == '_' || c == '.' || c == '@' }.take(Username.MAX_LENGTH + 1)) }
    }

    /**
     * "JODO": looks the username up in the People list and sends that person an invite. It becomes a friendship when they
     * accept. This needs no Cloud Function, so it works as soon as the Firestore rules are deployed.
     */
    fun addFriend() {
        val parsed = Username.parse(local.value.addText) as? UsernameResult.Valid
        if (parsed == null) { notice(Notice(R.string.friends_not_found, good = false)); return }
        if (local.value.adding) return
        local.update { it.copy(adding = true) }
        viewModelScope.launch {
            val person = peopleRepo.people.first().firstOrNull { it.username == parsed.username.value }
            if (person == null) {
                local.update { it.copy(adding = false, notice = Notice(R.string.friends_not_found, good = false)) }
                return@launch
            }
            val result = when {
                person.isFriend -> PersonInviteResult.ALREADY_FRIENDS
                person.invited -> PersonInviteResult.ALREADY_SENT
                else -> peopleRepo.invite(person.uid)
            }
            val clear = result == PersonInviteResult.SENT || result == PersonInviteResult.ALREADY_SENT || result == PersonInviteResult.ALREADY_FRIENDS
            local.update { it.copy(adding = false, addText = if (clear) "" else it.addText, notice = result.toNotice()) }
        }
    }

    /** Asks the server for a link and hands it to the share sheet, so it goes out by WhatsApp, SMS or anything else. */
    fun shareInviteLink() {
        if (local.value.sharing) return
        local.update { it.copy(sharing = true) }
        viewModelScope.launch {
            val code = friendsRepo.createInvite()
            local.update { it.copy(sharing = false) }
            if (code == null) {
                notice(Notice(R.string.friends_failed, good = false))
            } else {
                val link = InviteLinks(context.getString(R.string.invite_web_host)).build(code)
                shareChannel.trySend(context.getString(R.string.friends_share_message, link))
            }
        }
    }

    fun onContactsPermission(granted: Boolean) {
        if (granted) loadContacts() else local.update { it.copy(contacts = ContactsUi.Denied) }
    }

    fun loadContacts() {
        local.update { it.copy(contacts = ContactsUi.Loading) }
        viewModelScope.launch {
            val next = when (val result = contactsRepo.findFriends()) {
                is ContactsResult.Found -> ContactsUi.Found(result.matches)
                ContactsResult.NoPermission -> ContactsUi.Denied
                ContactsResult.NotSignedIn -> ContactsUi.Problem(notSignedIn = true, offline = false)
                ContactsResult.NoNetwork -> ContactsUi.Problem(notSignedIn = false, offline = true)
                ContactsResult.Failed -> ContactsUi.Problem(notSignedIn = false, offline = false)
            }
            local.update { it.copy(contacts = next) }
        }
    }

    private fun loadContactsIfAllowed() {
        if (contactsRepo.hasPermission()) loadContacts()
    }

    /** "JOIN KARO": a personal invite plus a push notification to a contact who already has the app. */
    fun inviteContact(uid: String) {
        if (local.value.busyUid != null) return
        local.update { it.copy(busyUid = uid) }
        viewModelScope.launch {
            // Same invite as the Log list: it lands in that person's inbox (no Cloud Function needed).
            val result = peopleRepo.invite(uid)
            local.update { s ->
                val done = result == PersonInviteResult.SENT || result == PersonInviteResult.ALREADY_SENT
                s.copy(busyUid = null, invited = if (done) s.invited + uid else s.invited, notice = result.toNotice())
            }
            if (result == PersonInviteResult.ALREADY_FRIENDS) loadContactsIfAllowed()
        }
    }

    fun onTab(tab: FriendsTab) {
        tabChosen = true
        local.update { it.copy(tab = tab) }
    }

    fun onSearch(text: String) = local.update { it.copy(search = text.take(30)) }

    /** "INVITE" in the People list: leaves one invite in that person's inbox (they get a notification). */
    fun invitePerson(uid: String) {
        if (local.value.invitingUid != null) return
        local.update { it.copy(invitingUid = uid) }
        viewModelScope.launch {
            val result = peopleRepo.invite(uid)
            local.update { it.copy(invitingUid = null, notice = result.toNotice()) }
        }
    }

    /** ACCEPT on a battle challenge: the start line is taken first, then the battle starts and its page opens. */
    fun acceptChallenge(duel: DuelInfo) {
        if (local.value.answeringDuel != null) return
        local.update { it.copy(answeringDuel = duel.id) }
        viewModelScope.launch {
            tracker.markStart(duel.id)
            val result = duelRepo.accept(duel.id)
            local.update { it.copy(answeringDuel = null, notice = if (result == DuelAction.OK) null else result.toNotice()) }
            if (result == DuelAction.OK) openDuelChannel.trySend(duel.id)
        }
    }

    /** NAHI on a broadcast invitation (or after joining it): the card goes away. */
    fun dismissBroadcastInvite(invite: BroadcastInvite) {
        viewModelScope.launch { broadcastRepo.dismissInvite(invite.fromUid) }
    }

    fun declineChallenge(duel: DuelInfo) {
        if (local.value.answeringDuel != null) return
        local.update { it.copy(answeringDuel = duel.id) }
        viewModelScope.launch {
            val result = duelRepo.decline(duel.id)
            local.update { it.copy(answeringDuel = null, notice = if (result == DuelAction.OK) Notice(R.string.invites_declined) else result.toNotice()) }
        }
    }

    private fun DuelAction.toNotice(): Notice = when (this) {
        DuelAction.OK -> Notice(R.string.invites_declined)
        DuelAction.NO_NETWORK -> Notice(R.string.friends_network, good = false)
        DuelAction.GONE -> Notice(R.string.invites_gone, good = false)
        DuelAction.NOT_SIGNED_IN -> Notice(R.string.friends_need_login, good = false)
        else -> Notice(R.string.friends_failed, good = false)
    }

    fun acceptInvite(invite: IncomingInvite) = answer(invite) { peopleRepo.accept(invite) }

    fun declineInvite(invite: IncomingInvite) = answer(invite) { peopleRepo.decline(invite) }

    private fun answer(invite: IncomingInvite, call: suspend () -> AnswerResult) {
        if (local.value.answeringUid != null) return
        local.update { it.copy(answeringUid = invite.fromUid) }
        viewModelScope.launch {
            val result = call()
            local.update { it.copy(answeringUid = null, notice = result.toNotice()) }
        }
    }

    private fun PersonInviteResult.toNotice(): Notice = when (this) {
        PersonInviteResult.SENT -> Notice(R.string.people_invite_sent)
        PersonInviteResult.ALREADY_SENT -> Notice(R.string.people_invite_already)
        PersonInviteResult.ALREADY_FRIENDS -> Notice(R.string.friends_already)
        PersonInviteResult.NO_PROFILE -> Notice(R.string.people_no_profile, good = false)
        PersonInviteResult.NO_NETWORK -> Notice(R.string.friends_network, good = false)
        PersonInviteResult.NOT_SIGNED_IN -> Notice(R.string.friends_need_login, good = false)
        PersonInviteResult.FAILED -> Notice(R.string.friends_failed, good = false)
    }

    private fun AnswerResult.toNotice(): Notice = when (this) {
        is AnswerResult.Accepted -> Notice(R.string.invites_accepted, friendName)
        AnswerResult.Declined -> Notice(R.string.invites_declined)
        AnswerResult.Gone -> Notice(R.string.invites_gone, good = false)
        AnswerResult.NoNetwork -> Notice(R.string.friends_network, good = false)
        AnswerResult.Failed -> Notice(R.string.friends_failed, good = false)
    }

    fun clearNotice() = local.update { it.copy(notice = null) }

    private fun notice(n: Notice) = local.update { it.copy(notice = n) }

    private fun AddFriendResult.toNotice(): Notice = when (this) {
        is AddFriendResult.Added -> Notice(R.string.friends_added, friend.username)
        AddFriendResult.AlreadyFriends -> Notice(R.string.friends_already)
        AddFriendResult.NotFound -> Notice(R.string.friends_not_found, good = false)
        AddFriendResult.CannotAddSelf -> Notice(R.string.friends_self, good = false)
        AddFriendResult.NoNetwork -> Notice(R.string.friends_network, good = false)
        AddFriendResult.NotSignedIn -> Notice(R.string.friends_need_login, good = false)
        AddFriendResult.Failed -> Notice(R.string.friends_failed, good = false)
    }

    private fun SendInviteResult.toNotice(): Notice = when (this) {
        SendInviteResult.SENT, SendInviteResult.ALREADY_SENT -> Notice(R.string.friends_invite_sent)
        SendInviteResult.ALREADY_FRIENDS -> Notice(R.string.friends_already)
        SendInviteResult.NOT_FOUND -> Notice(R.string.friends_not_found, good = false)
        SendInviteResult.NO_NETWORK -> Notice(R.string.friends_network, good = false)
        SendInviteResult.NOT_SIGNED_IN -> Notice(R.string.friends_need_login, good = false)
        SendInviteResult.FAILED -> Notice(R.string.friends_failed, good = false)
    }
}
