package com.doomscrollduel.feature.broadcast

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import com.doomscrollduel.domain.social.RoomInviteResult
import com.doomscrollduel.domain.social.Person
import com.doomscrollduel.domain.social.PeopleRepository
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.lifecycle.SavedStateHandle
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.doomscrollduel.R
import com.doomscrollduel.core.designsystem.components.DuelIcon
import com.doomscrollduel.core.designsystem.theme.Nunito
import com.doomscrollduel.domain.social.BroadcastMember
import com.doomscrollduel.domain.social.BroadcastProblem
import com.doomscrollduel.domain.social.BroadcastRepository
import com.doomscrollduel.domain.social.BroadcastRoomInfo
import com.doomscrollduel.domain.social.BroadcastState
import com.doomscrollduel.domain.social.BroadcastStatus
import com.doomscrollduel.domain.social.ChatMessage
import com.doomscrollduel.domain.social.PeerState
import com.doomscrollduel.feature.common.Kit
import com.doomscrollduel.feature.common.KitAvatar
import com.doomscrollduel.feature.common.KitButton
import com.doomscrollduel.feature.common.KitButtonKind
import com.doomscrollduel.feature.common.KitCard
import com.doomscrollduel.feature.common.KitField
import com.doomscrollduel.feature.common.KitIcons
import com.doomscrollduel.feature.common.KitPage
import com.doomscrollduel.feature.common.KitPill
import com.doomscrollduel.feature.modes.navyBackground
import com.doomscrollduel.feature.settings.neon.NText
import com.doomscrollduel.feature.settings.neon.Neon
import com.doomscrollduel.feature.settings.neon.NeonIcons
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class BroadcastViewModel @Inject constructor(
    private val repo: BroadcastRepository,
    peopleRepo: PeopleRepository,
    handle: SavedStateHandle,
) : ViewModel() {
    val state: StateFlow<BroadcastState> = repo.state
    val rooms: StateFlow<List<BroadcastRoomInfo>> = repo.rooms.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Everybody who could be asked into the room I am in. */
    val people: StateFlow<List<Person>> = peopleRepo.people.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** A room to join as soon as the page opens (it came from an invitation). */
    val joinRoomId: String? = handle.get<String>("join")

    private val _invited = MutableStateFlow<Set<String>>(emptySet())
    val invited: StateFlow<Set<String>> = _invited

    private val _notice = MutableStateFlow<RoomInviteResult?>(null)
    val notice: StateFlow<RoomInviteResult?> = _notice

    fun start(title: String) { viewModelScope.launch { repo.start(title) } }

    fun join(roomId: String) { viewModelScope.launch { repo.join(roomId) } }

    fun leave() { viewModelScope.launch { repo.leave() } }

    fun setMuted(muted: Boolean) = repo.setMuted(muted)

    fun setSpeaker(on: Boolean) = repo.setSpeaker(on)

    fun send(text: String) = repo.sendChat(text)

    fun clearProblem() = repo.clearProblem()

    fun invite(uid: String) {
        viewModelScope.launch {
            val result = repo.inviteToRoom(uid)
            if (result == RoomInviteResult.SENT || result == RoomInviteResult.TOO_SOON) _invited.update { it + uid }
            _notice.value = result
        }
    }

    fun clearNotice() { _notice.value = null }

    fun kick(uid: String) { viewModelScope.launch { repo.kick(uid) } }
}

// ----------------------------------------------------------------------------------------------------------- list page

/** The Broadcast page: start your own voice room, or join one a friend has opened. */
@Composable
fun BroadcastRoute(
    onBack: () -> Unit,
    onOpenRoom: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BroadcastViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rooms by viewModel.rooms.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var micDenied by remember { mutableStateOf(false) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micDenied = !granted
        if (granted) pending?.invoke()
        pending = null
    }
    fun withMic(action: () -> Unit) {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            micDenied = false
            action()
        } else {
            pending = action
            askMic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    // Came here from an invitation: join that room (asking for the microphone first).
    val inviteRoom = viewModel.joinRoomId
    var triedInvite by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(inviteRoom) {
        if (inviteRoom != null && !triedInvite && state.status != BroadcastStatus.LIVE) {
            triedInvite = true
            withMic { viewModel.join(inviteRoom) }
        }
    }
    // Going into the room happens when joining finishes, not when arriving here while already in one.
    var wasLive by remember { mutableStateOf(state.status == BroadcastStatus.LIVE) }
    LaunchedEffect(state.status) {
        if (state.status == BroadcastStatus.LIVE && !wasLive) onOpenRoom()
        wasLive = state.status == BroadcastStatus.LIVE
    }

    BroadcastListScreen(
        state = state,
        rooms = rooms,
        micDenied = micDenied,
        onBack = onBack,
        onStart = { title -> withMic { viewModel.start(title) } },
        onJoin = { id -> withMic { viewModel.join(id) } },
        onOpenRoom = onOpenRoom,
        onDismissProblem = viewModel::clearProblem,
        modifier = modifier,
    )
}

@Composable
fun BroadcastListScreen(
    state: BroadcastState,
    rooms: List<BroadcastRoomInfo>,
    micDenied: Boolean,
    onBack: () -> Unit,
    onStart: (String) -> Unit,
    onJoin: (String) -> Unit,
    onOpenRoom: () -> Unit,
    onDismissProblem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var title by rememberSaveable { mutableStateOf("") }
    val joining = state.status == BroadcastStatus.JOINING
    val live = state.status == BroadcastStatus.LIVE
    KitPage(modifier = modifier, title = stringResource(R.string.bc_title), onBack = onBack) {
        KitCard(
            radius = 24.dp,
            fill = Brush.horizontalGradient(listOf(Kit.Green.copy(alpha = 0.18f), Kit.Surface)),
            edge = Kit.Green.copy(alpha = 0.35f),
        ) {
            NText(stringResource(R.string.bc_intro), 14.sp, lineHeight = 20.sp)
        }

        val problem = problemText(state.problem, micDenied)
        if (problem != null) {
            Spacer(Modifier.height(12.dp))
            KitCard(
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                fill = Brush.horizontalGradient(listOf(Kit.Red.copy(alpha = 0.2f), Kit.Red.copy(alpha = 0.2f))),
                edge = Kit.Red.copy(alpha = 0.5f),
                onClick = onDismissProblem,
            ) { NText(problem, 14.sp, weight = FontWeight.Bold, lineHeight = 19.sp) }
        }

        Spacer(Modifier.height(18.dp))
        if (live) {
            KitCard(radius = 22.dp) {
                NText(stringResource(R.string.bc_in_room, state.title), 15.sp, weight = FontWeight.ExtraBold, lineHeight = 21.sp)
                Spacer(Modifier.height(10.dp))
                KitButton(stringResource(R.string.bc_back_to_room), onOpenRoom, icon = com.doomscrollduel.feature.common.KitIcons.Broadcast)
            }
        } else {
            NText(stringResource(R.string.bc_start_title), 18.sp, weight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            KitField(
                value = title,
                onValueChange = { title = it.take(40) },
                label = stringResource(R.string.bc_title_label),
                hint = stringResource(R.string.bc_title_hint),
                enabled = !joining,
                capitalization = KeyboardCapitalization.Sentences,
            )
            Spacer(Modifier.height(10.dp))
            KitButton(
                text = stringResource(if (joining) R.string.bc_joining else R.string.bc_start),
                onClick = { onStart(title) },
                enabled = !joining,
                icon = com.doomscrollduel.feature.common.KitIcons.Broadcast,
            )
        }

        Spacer(Modifier.height(22.dp))
        NText(stringResource(R.string.bc_live_rooms), 18.sp, weight = FontWeight.ExtraBold)
        Spacer(Modifier.height(10.dp))
        val others = rooms.filter { it.id != state.roomId }
        if (others.isEmpty()) {
            NText(stringResource(R.string.bc_no_rooms), 14.sp, color = Neon.Muted, lineHeight = 20.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                others.forEach { room ->
                    KitCard(radius = 22.dp, padding = PaddingValues(14.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            KitAvatar(room.hostName, tone = Kit.Green)
                            Column(Modifier.weight(1f)) {
                                NText(room.title, 16.sp, weight = FontWeight.ExtraBold, maxLines = 1)
                                NText(stringResource(R.string.bc_by, room.hostName), 12.sp, color = Neon.Muted, maxLines = 1)
                                NText(stringResource(R.string.bc_room_people, room.count, room.names.joinToString(", ")), 12.sp, color = Kit.Green, maxLines = 1)
                            }
                            Box(
                                Modifier
                                    .heightIn(min = 44.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Kit.Primary)
                                    .clickable(enabled = !joining, role = Role.Button) { onJoin(room.id) }
                                    .padding(horizontal = 18.dp),
                                contentAlignment = Alignment.Center,
                            ) { NText(stringResource(R.string.bc_join), 13.sp, weight = FontWeight.ExtraBold, maxLines = 1) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun problemText(problem: BroadcastProblem?, micDenied: Boolean): String? = when {
    micDenied -> stringResource(R.string.bc_mic_needed)
    problem == null -> null
    else -> stringResource(
        when (problem) {
            BroadcastProblem.NO_PROFILE -> R.string.bc_err_no_profile
            BroadcastProblem.NO_MIC -> R.string.bc_mic_needed
            BroadcastProblem.NOT_ALLOWED -> R.string.bc_err_not_allowed
            BroadcastProblem.NO_ROOM -> R.string.bc_err_no_room
            BroadcastProblem.NO_NETWORK -> R.string.bc_err_network
            BroadcastProblem.REMOVED -> R.string.bc_err_removed
            BroadcastProblem.ROOM_CLOSED -> R.string.bc_err_room_closed
            BroadcastProblem.FAILED -> R.string.bc_err_failed
        },
    )
}

// ----------------------------------------------------------------------------------------------------------- the room

@Composable
fun BroadcastRoomRoute(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BroadcastViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()
    val invited by viewModel.invited.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    // Left the room (or was removed): back to the list.
    LaunchedEffect(state.status) { if (state.status == BroadcastStatus.IDLE) onClose() }
    BroadcastRoomScreen(
        state = state,
        onBack = onClose,
        onLeave = viewModel::leave,
        onMuted = viewModel::setMuted,
        onSpeaker = viewModel::setSpeaker,
        onSend = viewModel::send,
        modifier = modifier,
        people = people,
        invited = invited,
        inviteNotice = notice,
        onInvite = viewModel::invite,
        onClearNotice = viewModel::clearNotice,
        onKick = viewModel::kick,
    )
}

/**
 * Who is in the room (profile circle and name), the mic and speaker switches, and the chat underneath. The chat is kept only
 * on this phone. "Back" keeps the room running in the background; "Chhodo" leaves it.
 */
@Composable
fun BroadcastRoomScreen(
    state: BroadcastState,
    onBack: () -> Unit,
    onLeave: () -> Unit,
    onMuted: (Boolean) -> Unit,
    onSpeaker: (Boolean) -> Unit,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
    people: List<Person> = emptyList(),
    invited: Set<String> = emptySet(),
    inviteNotice: RoomInviteResult? = null,
    onInvite: (String) -> Unit = {},
    onClearNotice: () -> Unit = {},
    onKick: (String) -> Unit = {},
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var showInvite by rememberSaveable { mutableStateOf(false) }
    val iAmHost = state.members.any { it.isMe && it.isHost }
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }
    Box(modifier.fillMaxSize().navyBackground()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            // header
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { DuelIcon(NeonIcons.Back, tint = Color.White, contentDescription = stringResource(R.string.paywall_back), modifier = Modifier.size(24.dp)) }
                NText(state.title, 20.sp, weight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.weight(1f))
                KitPill(stringResource(R.string.bc_room_live), tone = Kit.Red, filled = false)
            }

            // people
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                state.members.forEach { member ->
                    MemberTile(member, onKick = if (iAmHost && !member.isMe) ({ onKick(member.uid) }) else null)
                }
            }
            if (state.members.size <= 1) {
                NText(
                    stringResource(R.string.bc_alone), 13.sp, color = Neon.Muted, lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // switches: three small round icon buttons (the name is read aloud, the icon is what is seen)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundControl(
                    icon = if (state.muted) KitIcons.MicOff else KitIcons.Mic,
                    description = stringResource(if (state.muted) R.string.bc_unmute else R.string.bc_mute),
                    fill = if (state.muted) Kit.Red.copy(alpha = 0.9f) else Kit.SurfaceHigh,
                    onClick = { onMuted(!state.muted) },
                )
                RoundControl(
                    icon = if (state.speaker) KitIcons.Speaker else KitIcons.Earpiece,
                    description = stringResource(if (state.speaker) R.string.bc_speaker_on else R.string.bc_earpiece),
                    fill = Kit.SurfaceHigh,
                    onClick = { onSpeaker(!state.speaker) },
                )
                RoundControl(
                    icon = KitIcons.UserPlus,
                    description = stringResource(R.string.bc_invite_cd),
                    fill = Kit.SurfaceHigh,
                    onClick = { showInvite = true },
                )
                RoundControl(
                    icon = KitIcons.HangUp,
                    description = stringResource(if (state.members.any { it.isMe && it.isHost }) R.string.bc_close_room else R.string.bc_leave),
                    fill = Color(0xFFE5365A),
                    onClick = onLeave,
                )
            }
            AudioStatus(state)

            // chat
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (state.messages.isEmpty()) {
                    NText(
                        stringResource(R.string.bc_chat_empty), 13.sp, color = Neon.Muted, lineHeight = 18.sp, align = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                    )
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().semantics { liveRegion = LiveRegionMode.Polite },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.messages, key = { it.id }) { Bubble(it) }
                }
            }

            // input
            val shape = RoundedCornerShape(26.dp)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it.take(500) },
                    textStyle = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color.White),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (draft.isEmpty()) NText(stringResource(R.string.bc_chat_hint), 15.sp, color = Neon.Muted.copy(alpha = 0.7f), maxLines = 1)
                            inner()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .clip(shape)
                        .background(Kit.Surface)
                        .border(1.dp, Kit.Edge, shape)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                )
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Kit.Primary)
                        .clickable(enabled = draft.isNotBlank(), role = Role.Button) {
                            onSend(draft)
                            draft = ""
                        }
                        .semantics { contentDescription = "" },
                    contentAlignment = Alignment.Center,
                ) { DuelIcon(NeonIcons.ArrowRight, tint = Color.White, contentDescription = stringResource(R.string.bc_send), modifier = Modifier.size(22.dp)) }
            }
        }
        if (showInvite) {
            InvitePanel(
                people = people,
                invited = invited,
                notice = inviteNotice,
                onInvite = onInvite,
                onClearNotice = onClearNotice,
                onClose = { showInvite = false },
            )
        }
    }
}

/** A sheet over the room: search everybody and ask them in. Friends come first. */
@Composable
private fun InvitePanel(
    people: List<Person>,
    invited: Set<String>,
    notice: RoomInviteResult?,
    onInvite: (String) -> Unit,
    onClearNotice: () -> Unit,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(people, query) {
        val q = query.trim().lowercase()
        people.filter { q.isEmpty() || q in it.username.lowercase() || q in it.displayName.lowercase() }
            .sortedWith(compareByDescending<Person> { it.isFriend }.thenBy { it.displayName.lowercase() })
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color(0xAA000000)).clickable(onClick = onClose))
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Kit.Surface)
                .navigationBarsPadding()
                .imePadding()
                .padding(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                NText(stringResource(R.string.bc_invite_title), 20.sp, weight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Box(
                    Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = onClose).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { NText(stringResource(R.string.bc_invite_close), 14.sp, color = Neon.VioletLight, weight = FontWeight.ExtraBold) }
            }
            Spacer(Modifier.height(10.dp))
            KitField(
                value = query,
                onValueChange = { query = it.take(30) },
                label = stringResource(R.string.people_search_label),
                hint = stringResource(R.string.bc_invite_search_hint),
            )
            val message = when (notice) {
                null -> null
                RoomInviteResult.SENT -> R.string.bc_invite_sent to Kit.Green
                RoomInviteResult.TOO_SOON -> R.string.bc_invite_wait to Kit.Orange
                else -> R.string.bc_invite_failed to Kit.Red
            }
            if (message != null) {
                Spacer(Modifier.height(8.dp))
                NText(
                    stringResource(message.first), 13.sp, color = message.second, weight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onClearNotice).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Spacer(Modifier.height(10.dp))
            if (shown.isEmpty()) {
                NText(stringResource(R.string.bc_invite_none), 14.sp, color = Neon.Muted)
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(shown, key = { it.uid }) { person ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 60.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            KitAvatar(person.displayName, tone = if (person.isFriend) Kit.Pink else Kit.Blue)
                            Column(Modifier.weight(1f)) {
                                NText(person.displayName, 15.sp, weight = FontWeight.ExtraBold, maxLines = 1)
                                NText("@${person.username}", 12.sp, color = Neon.Muted, maxLines = 1)
                            }
                            if (person.uid in invited) {
                                KitPill(stringResource(R.string.bc_invited), tone = Kit.Violet)
                            } else {
                                Box(
                                    Modifier
                                        .heightIn(min = 44.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(Kit.Primary)
                                        .clickable(role = Role.Button) { onInvite(person.uid) }
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.Center,
                                ) { NText(stringResource(R.string.bc_invite_btn), 13.sp, weight = FontWeight.ExtraBold, maxLines = 1) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A 46dp round button with only an icon. */
@Composable
private fun RoundControl(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, fill: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, Kit.Edge, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { DuelIcon(icon, tint = Color.White, contentDescription = null, modifier = Modifier.size(22.dp)) }
}

/** One line that answers "is my voice going out, and is theirs coming in?" from the audio that is really flowing. */
@Composable
private fun AudioStatus(state: BroadcastState) {
    val others = state.members.filter { !it.isMe && it.state == PeerState.CONNECTED }
    val going = others.count { it.sending }
    val coming = others.count { it.receiving }
    val text = when {
        state.members.size <= 1 -> return
        others.isEmpty() -> stringResource(R.string.bc_audio_connecting)
        state.muted -> stringResource(R.string.bc_audio_muted)
        going == 0 -> stringResource(R.string.bc_audio_not_going)
        else -> stringResource(R.string.bc_audio_ok, going, coming)
    }
    val tone = if (others.isEmpty() || state.muted) Neon.Muted else if (going == 0) Kit.Orange else Kit.Green
    NText(
        text, 12.sp, color = tone, weight = FontWeight.Bold, align = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).semantics { liveRegion = LiveRegionMode.Polite },
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun MemberTile(member: BroadcastMember, onKick: (() -> Unit)? = null) {
    val ring = when (member.state) {
        PeerState.CONNECTED -> Kit.Green
        PeerState.CONNECTING -> Kit.Orange
        PeerState.FAILED -> Kit.Red
    }
    val stateText = stringResource(
        when (member.state) {
            PeerState.CONNECTED -> R.string.bc_state_connected
            PeerState.CONNECTING -> R.string.bc_state_connecting
            PeerState.FAILED -> R.string.bc_state_failed
        },
    )
    val speakingText = if (member.speaking) stringResource(R.string.bc_speaking) else ""
    // Two soft rings that grow and fade while the person talks; still (just brighter) when animations are off.
    val reduced = com.doomscrollduel.core.designsystem.theme.DuelTheme.motion.reduced
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "voice")
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.LinearEasing)),
        label = "wave",
    )
    Column(
        Modifier.widthIn(min = 76.dp, max = 100.dp).semantics(mergeDescendants = true) { contentDescription = "${member.name}, $stateText $speakingText" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // The avatar with its ring is 70dp across. The pulse rings start at exactly that size and grow outwards to 1.5x while they
        // fade, so they appear around the circle (a smaller ring would hide behind it). 100dp of room is left for them.
        Box(Modifier.size(100.dp), contentAlignment = Alignment.Center) {
            if (member.speaking) {
                if (reduced) {
                    Box(Modifier.size(88.dp).clip(CircleShape).background(Kit.Green.copy(alpha = 0.35f)))
                } else {
                    listOf(wave, (wave + 0.5f) % 1f).forEach { t ->
                        Box(
                            Modifier
                                .size(70.dp)
                                .graphicsLayer {
                                    val scale = 1f + 0.45f * t
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = 0.6f * (1f - t)
                                }
                                .clip(CircleShape)
                                .background(Kit.Green),
                        )
                    }
                }
            }
            Box(Modifier.border(if (member.speaking) 4.dp else 3.dp, if (member.speaking) Kit.Green else ring, CircleShape).padding(3.dp)) {
                KitAvatar(member.name, size = 58.dp, tone = if (member.isMe) Kit.Pink else Kit.Blue)
            }
            if (onKick != null) {
                // The host's "remove": a small red cross on the corner of the person's circle.
                val kickText = stringResource(R.string.bc_kick_cd, member.name)
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Kit.Red)
                        .clickable(role = Role.Button, onClick = onKick)
                        .semantics { contentDescription = kickText },
                    contentAlignment = Alignment.Center,
                ) { NText("×", 18.sp, weight = FontWeight.Black) }
            }
        }
        NText(member.name, 13.sp, weight = FontWeight.ExtraBold, maxLines = 1, align = TextAlign.Center)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            NText(
                stringResource(if (member.isMe) R.string.bc_you else if (member.isHost) R.string.bc_host else R.string.bc_member),
                11.sp, color = Neon.Muted, maxLines = 1,
            )
            if (!member.isMe && member.state == PeerState.CONNECTED) {
                DuelIcon(KitIcons.ArrowUp, tint = if (member.sending) Kit.Green else Kit.Track, contentDescription = null, modifier = Modifier.size(12.dp))
                DuelIcon(KitIcons.ArrowUp, tint = if (member.receiving) Kit.Green else Kit.Track, contentDescription = null, modifier = Modifier.size(12.dp).graphicsLayer { rotationZ = 180f })
            }
        }
    }
}

@Composable
private fun Bubble(message: ChatMessage) {
    val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (message.mine) 18.dp else 4.dp, bottomEnd = if (message.mine) 4.dp else 18.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(if (message.mine) Brush.horizontalGradient(listOf(Kit.Pink, Kit.VioletDeep)) else Brush.verticalGradient(listOf(Kit.SurfaceHigh, Kit.SurfaceHigh)))
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            if (!message.mine) NText(message.fromName, 12.sp, color = Neon.VioletLight, weight = FontWeight.ExtraBold, maxLines = 1)
            NText(message.text, 15.sp, lineHeight = 20.sp)
        }
    }
}
