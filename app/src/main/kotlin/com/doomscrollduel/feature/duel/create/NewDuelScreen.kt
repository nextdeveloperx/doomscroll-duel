package com.doomscrollduel.feature.duel.create

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import com.doomscrollduel.feature.common.KitUsernamePrompt
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import com.doomscrollduel.domain.social.DuelAction
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doomscrollduel.R
import com.doomscrollduel.core.designsystem.brain.BrainOwner
import com.doomscrollduel.feature.FakeData
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.doomscrollduel.core.designsystem.components.ScreenPreview
import com.doomscrollduel.feature.common.Kit
import com.doomscrollduel.feature.common.KitBottomButton
import com.doomscrollduel.feature.common.KitAvatar
import com.doomscrollduel.feature.common.KitCard
import com.doomscrollduel.feature.common.KitDivider
import com.doomscrollduel.feature.common.KitField
import com.doomscrollduel.feature.common.KitPill
import androidx.compose.foundation.layout.heightIn
import com.doomscrollduel.feature.common.KitChoice
import com.doomscrollduel.feature.common.KitFighter
import com.doomscrollduel.feature.common.KitPage
import com.doomscrollduel.feature.common.KitVs
import com.doomscrollduel.feature.settings.neon.NText
import com.doomscrollduel.feature.settings.neon.Neon
import com.doomscrollduel.feature.settings.neon.NeonIcons

enum class DurationOption(val hours: Int) {
    SIX_HOURS(6),
    ONE_DAY(24),
    SEVEN_DAYS(168),
}

val StakeOptions = listOf(25, 50, 100)

const val MinReelLimit = 10
const val MaxReelLimit = 500
const val ReelLimitStep = 10

@Immutable
data class NewDuelUiState(
    val friends: List<String>,
    val selectedFriendIndex: Int,
    val reelLimit: Int,
    val duration: DurationOption,
    val stakeCoins: Int,
) {
    val friendName: String get() = friends.getOrElse(selectedFriendIndex) { friends.firstOrNull().orEmpty() }
}

/** Asks the friends list and sends the challenge. */
@dagger.hilt.android.lifecycle.HiltViewModel
class NewDuelViewModel @javax.inject.Inject constructor(
    friendsRepo: com.doomscrollduel.domain.repository.FriendsRepository,
    private val duels: com.doomscrollduel.domain.social.DuelRepository,
    private val peopleRepo: com.doomscrollduel.domain.social.PeopleRepository,
) : androidx.lifecycle.ViewModel() {
    /** Everybody who signed in (for the search box), with who is already a friend or already asked. */
    val people: kotlinx.coroutines.flow.StateFlow<List<com.doomscrollduel.domain.social.Person>> = peopleRepo.people.stateIn(
        viewModelScope,
        kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    suspend fun sendRequest(uid: String) = peopleRepo.invite(uid)

    val friends: kotlinx.coroutines.flow.StateFlow<List<com.doomscrollduel.domain.repository.Friend>> = friendsRepo.friends.stateIn(
        viewModelScope,
        kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    suspend fun challenge(friend: com.doomscrollduel.domain.repository.Friend, form: NewDuelUiState) =
        duels.challenge(friend, form.reelLimit, form.duration.hours, form.stakeCoins)
}

/** Holds the form while the screen is open; survives rotation and process death. The friends are the real ones. */
@Composable
fun NewDuelRoute(
    onBack: () -> Unit,
    /** The challenge was saved: its id and the settings it was sent with. */
    onSent: (String, NewDuelUiState) -> Unit,
    onOpenFriends: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewDuelViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var requestingUid by remember { mutableStateOf<String?>(null) }
    var requestNote by remember { mutableStateOf<Int?>(null) }
    var friendIndex by rememberSaveable { mutableIntStateOf(0) }
    var limit by rememberSaveable { mutableIntStateOf(100) }
    var durationOrdinal by rememberSaveable { mutableIntStateOf(DurationOption.ONE_DAY.ordinal) }
    var stake by rememberSaveable { mutableIntStateOf(50) }
    var sending by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val state = NewDuelUiState(
        friends = friends.map { it.displayName },
        selectedFriendIndex = friendIndex.coerceIn(0, (friends.size - 1).coerceAtLeast(0)),
        reelLimit = limit,
        duration = DurationOption.entries[durationOrdinal],
        stakeCoins = stake,
    )

    NewDuelScreen(
        state = state,
        onFriendSelected = { friendIndex = it },
        query = query,
        onQueryChange = { query = it.take(30); requestNote = null },
        results = query.trim().lowercase().let { q ->
            if (q.isEmpty()) emptyList()
            else people.filter { q in it.username.lowercase() || q in it.displayName.lowercase() }.take(SEARCH_RESULTS)
        },
        requestingUid = requestingUid,
        requestNote = requestNote?.let { stringResource(it) },
        onPickPerson = { person -> friends.indexOfFirst { it.uid == person.uid }.takeIf { it >= 0 }?.let { friendIndex = it } },
        onRequest = { person ->
            if (requestingUid == null) {
                requestingUid = person.uid
                scope.launch {
                    val result = viewModel.sendRequest(person.uid)
                    requestingUid = null
                    requestNote = when (result) {
                        com.doomscrollduel.domain.social.PersonInviteResult.SENT,
                        com.doomscrollduel.domain.social.PersonInviteResult.ALREADY_SENT -> R.string.nd_request_sent
                        com.doomscrollduel.domain.social.PersonInviteResult.NO_NETWORK -> R.string.friends_network
                        com.doomscrollduel.domain.social.PersonInviteResult.NOT_SIGNED_IN -> R.string.friends_need_login
                        else -> R.string.friends_failed
                    }
                }
            }
        },
        onLimitChange = { limit = it.coerceIn(MinReelLimit, MaxReelLimit) },
        onDurationSelected = { durationOrdinal = it.ordinal },
        onStakeSelected = { stake = it },
        onBack = onBack,
        onSend = {
            val friend = friends.getOrNull(state.selectedFriendIndex)
            if (friend != null && !sending) {
                sending = true
                problem = null
                scope.launch {
                    val outcome = viewModel.challenge(friend, state)
                    sending = false
                    if (outcome.action == DuelAction.OK && outcome.duelId != null) {
                        onSent(outcome.duelId, state)
                    } else {
                        problem = when (outcome.action) {
                            DuelAction.NO_NETWORK -> R.string.friends_network
                            DuelAction.NOT_FRIENDS -> R.string.nd_err_not_friends
                            DuelAction.NOT_SIGNED_IN -> R.string.friends_need_login
                            else -> R.string.friends_failed
                        }
                    }
                }
            }
        },
        modifier = modifier,
        sending = sending,
        errorText = problem?.let { stringResource(it) },
        onOpenFriends = onOpenFriends,
    )
}

@Composable
fun NewDuelScreen(
    state: NewDuelUiState,
    onFriendSelected: (Int) -> Unit,
    onLimitChange: (Int) -> Unit,
    onDurationSelected: (DurationOption) -> Unit,
    onStakeSelected: (Int) -> Unit,
    onBack: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    results: List<com.doomscrollduel.domain.social.Person> = emptyList(),
    requestingUid: String? = null,
    requestNote: String? = null,
    onPickPerson: (com.doomscrollduel.domain.social.Person) -> Unit = {},
    onRequest: (com.doomscrollduel.domain.social.Person) -> Unit = {},
    sending: Boolean = false,
    errorText: String? = null,
    onOpenFriends: () -> Unit = {},
) {
    val noFriends = state.friends.isEmpty()
    KitPage(
        modifier = modifier,
        title = stringResource(R.string.nd_title),
        onBack = onBack,
        bottom = {
            KitBottomButton(
                text = stringResource(if (sending) R.string.nd_sending else R.string.nd_send),
                onClick = onSend,
                icon = NeonIcons.Swords,
                enabled = !noFriends && !sending,
            )
        },
    ) {
        if (errorText != null) {
            KitCard(
                fill = Brush.horizontalGradient(listOf(Kit.Red.copy(alpha = 0.2f), Kit.Red.copy(alpha = 0.2f))),
                edge = Kit.Red.copy(alpha = 0.5f),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            ) { NText(errorText, 14.sp, weight = FontWeight.Bold, lineHeight = 19.sp) }
            Spacer(Modifier.height(12.dp))
        }
        if (noFriends) {
            KitUsernamePrompt(
                title = stringResource(R.string.nd_no_friends_title),
                body = stringResource(R.string.nd_no_friends_body),
                button = stringResource(R.string.nd_no_friends_button),
                onChoose = onOpenFriends,
            )
            Spacer(Modifier.height(14.dp))
        }
        SearchSection(query, onQueryChange, results, state.friendName.takeIf { !noFriends }, requestingUid, requestNote, onPickPerson, onRequest)
        Spacer(Modifier.height(14.dp))
        KitCard(radius = 26.dp, padding = PaddingValues(vertical = 18.dp, horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                KitFighter(stringResource(R.string.label_you), 0, state.reelLimit, BrainOwner.YOU, Modifier.weight(1f), brainWidth = 96.dp)
                KitVs()
                KitFighter(state.friendName.ifEmpty { "?" }, 0, state.reelLimit, BrainOwner.OPPONENT, Modifier.weight(1f), brainWidth = 96.dp)
            }
        }

        if (!noFriends) {
            SectionLabel(R.string.nd_section_friend)
            ChipRow {
                state.friends.forEachIndexed { index, friend ->
                    KitChoice(friend, index == state.selectedFriendIndex, { onFriendSelected(index) })
                }
            }
        }

        SectionLabel(R.string.nd_section_limit)
        LimitStepper(limit = state.reelLimit, onLimitChange = onLimitChange)

        SectionLabel(R.string.nd_section_time)
        ChipRow {
            DurationOption.entries.forEach { option ->
                KitChoice(stringResource(option.labelRes()), option == state.duration, { onDurationSelected(option) })
            }
        }

        SectionLabel(R.string.nd_section_stake)
        ChipRow {
            StakeOptions.forEach { coins ->
                KitChoice(stringResource(R.string.nd_stake_option, coins), coins == state.stakeCoins, { onStakeSelected(coins) })
            }
        }

        Spacer(Modifier.height(20.dp))
        KitCard(fill = Brush.horizontalGradient(listOf(Kit.Violet.copy(alpha = 0.18f), Kit.Surface))) {
            NText(stringResource(R.string.nd_explain, state.reelLimit, state.stakeCoins, state.friendName), 15.sp, weight = FontWeight.Bold, lineHeight = 21.sp)
            Spacer(Modifier.height(6.dp))
            NText(stringResource(R.string.nd_virtual_note), 12.sp, color = Neon.Muted, lineHeight = 16.sp)
        }
    }
}

/** Search everybody by name or username: a friend can be picked for the battle, anybody else gets a friend request. */
@Composable
private fun SearchSection(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<com.doomscrollduel.domain.social.Person>,
    pickedName: String?,
    requestingUid: String?,
    note: String?,
    onPick: (com.doomscrollduel.domain.social.Person) -> Unit,
    onRequest: (com.doomscrollduel.domain.social.Person) -> Unit,
) {
    SectionLabel(R.string.nd_search_label)
    KitField(
        value = query,
        onValueChange = onQueryChange,
        label = stringResource(R.string.people_search_label),
        hint = stringResource(R.string.nd_search_hint),
    )
    if (note != null) {
        NText(note, 13.sp, weight = FontWeight.Bold, color = Neon.VioletLight, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })
    }
    if (query.isBlank()) return
    Spacer(Modifier.height(10.dp))
    if (results.isEmpty()) {
        NText(stringResource(R.string.people_none_found), 14.sp, color = Neon.Muted, modifier = Modifier.padding(vertical = 8.dp))
        return
    }
    KitCard(padding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
        results.forEachIndexed { i, person ->
            if (i > 0) KitDivider()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                KitAvatar(person.displayName)
                Column(Modifier.weight(1f)) {
                    NText(person.displayName, 15.sp, weight = FontWeight.ExtraBold, maxLines = 1)
                    NText("@" + person.username, 12.sp, color = Neon.Muted, maxLines = 1)
                }
                when {
                    person.isFriend && person.displayName == pickedName ->
                        KitPill(stringResource(R.string.nd_picked), tone = Kit.Green, filled = true)
                    person.isFriend -> SearchAction(stringResource(R.string.nd_pick_friend), true) { onPick(person) }
                    person.invited -> KitPill(stringResource(R.string.people_invited), tone = Kit.Violet)
                    else -> SearchAction(stringResource(R.string.nd_request_button), requestingUid == null) { onRequest(person) }
                }
            }
        }
    }
}

@Composable
private fun SearchAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
            .background(if (enabled) Kit.Violet else Kit.Track.copy(alpha = 0.4f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { NText(text, 12.sp, weight = FontWeight.ExtraBold, color = Color.White) }
}

private const val SEARCH_RESULTS = 8

private fun DurationOption.labelRes(): Int = when (this) {
    DurationOption.SIX_HOURS -> R.string.nd_duration_6h
    DurationOption.ONE_DAY -> R.string.nd_duration_24h
    DurationOption.SEVEN_DAYS -> R.string.nd_duration_7d
}

@Composable
private fun SectionLabel(textRes: Int) {
    NText(
        text = stringResource(textRes),
        size = 15.sp,
        weight = FontWeight.ExtraBold,
        color = Neon.VioletLight,
        modifier = Modifier.padding(top = 20.dp, bottom = 10.dp),
    )
}

/** Chips scroll sideways instead of wrapping, so narrow phones never push the layout around. */
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun LimitStepper(limit: Int, onLimitChange: (Int) -> Unit) {
    val spoken = stringResource(R.string.nd_limit_value, limit)
    KitCard(radius = 24.dp, padding = PaddingValues(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StepDisc(plus = false, description = stringResource(R.string.nd_limit_decrease), enabled = limit > MinReelLimit) { onLimitChange(limit - ReelLimitStep) }
            NText(
                text = limit.toString(),
                size = 48.sp,
                weight = FontWeight.Black,
                align = TextAlign.Center,
                modifier = Modifier.weight(1f).semantics { contentDescription = spoken },
            )
            StepDisc(plus = true, description = stringResource(R.string.nd_limit_increase), enabled = limit < MaxReelLimit) { onLimitChange(limit + ReelLimitStep) }
        }
    }
}

/** A round minus or plus button, 56dp so it is easy to hit. */
@Composable
private fun StepDisc(plus: Boolean, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(if (enabled) Kit.SurfaceHigh else Kit.Track.copy(alpha = 0.4f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (enabled) Color.White else Neon.Muted.copy(alpha = 0.4f)
        Box(Modifier.size(width = 20.dp, height = 3.dp).background(tint))
        if (plus) Box(Modifier.size(width = 3.dp, height = 20.dp).background(tint))
    }
}

@Preview(name = "New duel 390x844", widthDp = 390, heightDp = 844)
@Composable
private fun NewDuelPreview() = ScreenPreview {
    NewDuelScreen(
        state = FakeData.newDuel,
        onFriendSelected = {}, onLimitChange = {}, onDurationSelected = {}, onStakeSelected = {},
        onBack = {}, onSend = {},
    )
}
