package com.doomscrollduel.feature.friends

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doomscrollduel.R
import com.doomscrollduel.core.common.shareText
import com.doomscrollduel.domain.repository.Friend
import com.doomscrollduel.domain.social.ContactMatch
import com.doomscrollduel.domain.social.BroadcastInvite
import com.doomscrollduel.domain.social.DuelInfo
import com.doomscrollduel.domain.social.IncomingInvite
import com.doomscrollduel.domain.social.Person
import com.doomscrollduel.domain.social.Profile
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import com.doomscrollduel.core.designsystem.components.ScreenPreview
import com.doomscrollduel.feature.common.Kit
import com.doomscrollduel.feature.common.KitAvatar
import com.doomscrollduel.feature.common.KitButton
import com.doomscrollduel.feature.common.KitButtonKind
import com.doomscrollduel.feature.common.KitCard
import com.doomscrollduel.feature.common.KitDivider
import com.doomscrollduel.feature.common.KitField
import com.doomscrollduel.feature.common.KitPage
import com.doomscrollduel.feature.common.KitUsernamePrompt
import com.doomscrollduel.feature.common.KitPill
import com.doomscrollduel.feature.settings.neon.NText
import com.doomscrollduel.feature.settings.neon.Neon
import com.doomscrollduel.feature.settings.neon.NeonIcons

@Composable
fun FriendsRoute(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
    onChooseUsername: () -> Unit = {},
    onOpenDuel: (String) -> Unit = {},
    onJoinBroadcast: (String) -> Unit = {},
    viewModel: FriendsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) { viewModel.shareRequests.collect { context.shareText(it) } }
    LaunchedEffect(viewModel) { viewModel.openDuel.collect { onOpenDuel(it) } }
    val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), viewModel::onContactsPermission)
    // Invites arrive as phone notifications, so ask for that permission here, where the person sees why (Android 13 and up).
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    FriendsScreen(
        ui = ui,
        onBack = onBack,
        onLogin = onLogin,
        onShareLink = viewModel::shareInviteLink,
        onAddText = viewModel::onAddText,
        onAddFriend = viewModel::addFriend,
        onAllowContacts = { askContacts.launch(Manifest.permission.READ_CONTACTS) },
        onReloadContacts = viewModel::loadContacts,
        onInvite = viewModel::inviteContact,
        onNoticeDone = viewModel::clearNotice,
        modifier = modifier,
        onTab = viewModel::onTab,
        onSearch = viewModel::onSearch,
        onInvitePerson = viewModel::invitePerson,
        onAccept = viewModel::acceptInvite,
        onDecline = viewModel::declineInvite,
        onChooseUsername = onChooseUsername,
        onAcceptChallenge = viewModel::acceptChallenge,
        onDeclineChallenge = viewModel::declineChallenge,
        onJoinBroadcast = { invite ->
            viewModel.dismissBroadcastInvite(invite)
            onJoinBroadcast(invite.roomId)
        },
        onDismissBroadcast = viewModel::dismissBroadcastInvite,
    )
}

/** Dost: my username and invite link, add by username, contacts who already have the app, and the friends list. */
@Composable
fun FriendsScreen(
    ui: FriendsUi,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onShareLink: () -> Unit,
    onAddText: (String) -> Unit,
    onAddFriend: () -> Unit,
    onAllowContacts: () -> Unit,
    onReloadContacts: () -> Unit,
    onInvite: (String) -> Unit,
    onNoticeDone: () -> Unit,
    modifier: Modifier = Modifier,
    onTab: (FriendsTab) -> Unit = {},
    onSearch: (String) -> Unit = {},
    onInvitePerson: (String) -> Unit = {},
    onAccept: (IncomingInvite) -> Unit = {},
    onDecline: (IncomingInvite) -> Unit = {},
    onChooseUsername: () -> Unit = {},
    onAcceptChallenge: (DuelInfo) -> Unit = {},
    onDeclineChallenge: (DuelInfo) -> Unit = {},
    onJoinBroadcast: (BroadcastInvite) -> Unit = {},
    onDismissBroadcast: (BroadcastInvite) -> Unit = {},
) {
    KitPage(modifier = modifier, title = stringResource(R.string.friends_title), onBack = onBack) {
        ui.notice?.let { notice ->
            LaunchedEffect(notice) {
                kotlinx.coroutines.delay(3_500)
                onNoticeDone()
            }
            val tone = if (notice.good) Kit.Green else Kit.Red
            KitCard(
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                fill = Brush.horizontalGradient(listOf(tone.copy(alpha = 0.2f), tone.copy(alpha = 0.2f))),
                edge = tone.copy(alpha = 0.5f),
                onClick = onNoticeDone,
            ) {
                val text = if (notice.arg != null) stringResource(notice.text, notice.arg) else stringResource(notice.text)
                NText(text, 14.sp, weight = FontWeight.Bold, lineHeight = 19.sp)
            }
            Spacer(Modifier.height(12.dp))
        }

        if (!ui.signedIn) {
            KitCard(padding = PaddingValues(18.dp)) {
                NText(stringResource(R.string.friends_need_login), 15.sp, weight = FontWeight.Bold, lineHeight = 21.sp)
                Spacer(Modifier.height(14.dp))
                KitButton(stringResource(R.string.friends_login_button), onLogin)
            }
            return@KitPage
        }

        if (ui.backendDown) {
            KitCard(fill = Brush.horizontalGradient(listOf(Kit.Orange.copy(alpha = 0.2f), Kit.Orange.copy(alpha = 0.2f))), edge = Kit.Orange.copy(alpha = 0.5f)) {
                NText(stringResource(R.string.friends_backend_down), 14.sp, weight = FontWeight.Bold, lineHeight = 19.sp)
            }
            return@KitPage
        }

        if (ui.me == null) {
            // Without a username nobody can find this person and they are not in the Log list; say so and fix it in one tap.
            KitUsernamePrompt(
                title = stringResource(R.string.need_username_title),
                body = stringResource(R.string.need_username_body),
                button = stringResource(R.string.need_username_button),
                onChoose = onChooseUsername,
            )
            Spacer(Modifier.height(14.dp))
        }
        FriendsTabs(ui.tab, ui.incoming.size + ui.challenges.size + ui.broadcastInvites.size, onTab)
        Spacer(Modifier.height(14.dp))
        when (ui.tab) {
            FriendsTab.INVITES -> InvitesTab(ui, onAccept, onDecline, onAcceptChallenge, onDeclineChallenge, onJoinBroadcast, onDismissBroadcast)
            FriendsTab.PEOPLE -> PeopleTab(ui, onSearch, onInvitePerson)
            FriendsTab.FRIENDS -> {
            MyInviteCard(ui, onShareLink)
            Section(R.string.friends_add_title)
            KitField(
                value = ui.addText,
                onValueChange = onAddText,
                label = stringResource(R.string.friends_add_label),
                hint = stringResource(R.string.friends_add_hint),
                enabled = !ui.adding,
            )
            Spacer(Modifier.height(10.dp))
            KitButton(
                text = stringResource(R.string.friends_add_button),
                onClick = onAddFriend,
                enabled = ui.addText.isNotBlank() && !ui.adding,
                icon = NeonIcons.Plus,
            )

            Section(R.string.friends_contacts_title)
            ContactsSection(ui, onAllowContacts, onReloadContacts, onInvite)

            Section(R.string.friends_list_title)
            if (ui.friends.isEmpty()) {
                NText(stringResource(R.string.friends_list_empty), 14.sp, color = Neon.Muted, lineHeight = 20.sp)
            } else {
                KitCard(padding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                    ui.friends.forEachIndexed { i, friend ->
                        if (i > 0) KitDivider()
                        PersonRow(name = friend.displayName, username = friend.username, trailing = null)
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun Section(titleRes: Int) {
    NText(
        text = stringResource(titleRes),
        size = 18.sp,
        weight = FontWeight.ExtraBold,
        modifier = Modifier.padding(top = 24.dp, bottom = 10.dp).semantics { heading() },
    )
}

@Composable
private fun MyInviteCard(ui: FriendsUi, onShare: () -> Unit) {
    KitCard(
        radius = 24.dp,
        fill = Brush.horizontalGradient(listOf(Kit.Pink.copy(alpha = 0.30f), Kit.VioletDeep.copy(alpha = 0.30f))),
        padding = PaddingValues(18.dp),
    ) {
        NText(stringResource(R.string.friends_my_username), 13.sp, color = Color(0xFFE5DAFF), weight = FontWeight.Bold)
        NText(ui.me?.let { "@" + it.username } ?: "…", 28.sp, weight = FontWeight.Black, maxLines = 1)
        Spacer(Modifier.height(14.dp))
        KitButton(
            text = stringResource(R.string.friends_share),
            onClick = onShare,
            enabled = ui.me != null && !ui.sharing,
            icon = NeonIcons.Gift,
        )
    }
}

@Composable
private fun ContactsSection(ui: FriendsUi, onAllow: () -> Unit, onReload: () -> Unit, onInvite: (String) -> Unit) {
    when (val c = ui.contacts) {
        ContactsUi.NotAsked -> {
            NText(stringResource(R.string.friends_contacts_why), 14.sp, color = Neon.Muted, lineHeight = 20.sp)
            Spacer(Modifier.height(10.dp))
            KitButton(stringResource(R.string.friends_contacts_ask), onAllow)
        }
        ContactsUi.Denied -> {
            NText(stringResource(R.string.friends_contacts_denied), 14.sp, color = Kit.Orange, lineHeight = 20.sp)
            Spacer(Modifier.height(10.dp))
            KitButton(stringResource(R.string.friends_contacts_ask), onAllow, kind = KitButtonKind.Secondary)
        }
        ContactsUi.Loading -> NText(stringResource(R.string.friends_contacts_loading), 14.sp, color = Neon.Muted)
        is ContactsUi.Problem -> {
            NText(
                stringResource(if (c.offline) R.string.friends_network else if (c.notSignedIn) R.string.friends_need_login else R.string.friends_failed),
                14.sp, color = Kit.Orange, lineHeight = 20.sp,
            )
            Spacer(Modifier.height(10.dp))
            KitButton(stringResource(R.string.friends_contacts_retry), onReload, kind = KitButtonKind.Secondary)
        }
        is ContactsUi.Found -> {
            if (c.matches.isEmpty()) {
                NText(stringResource(R.string.friends_contacts_none), 14.sp, color = Neon.Muted, lineHeight = 20.sp)
            } else {
                KitCard(padding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                    c.matches.forEachIndexed { i, match ->
                        if (i > 0) KitDivider()
                        ContactRow(match, sent = match.uid in ui.invited, busy = ui.busyUid == match.uid, onInvite = { onInvite(match.uid) })
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            KitButton(stringResource(R.string.friends_contacts_retry), onReload, kind = KitButtonKind.Secondary)
        }
    }
}

@Composable
private fun ContactRow(match: ContactMatch, sent: Boolean, busy: Boolean, onInvite: () -> Unit) {
    PersonRow(
        name = match.displayName,
        username = match.username,
        trailing = {
            when {
                match.isFriend -> KitPill(stringResource(R.string.friends_is_friend), tone = Kit.Green)
                sent -> KitPill(stringResource(R.string.friends_invite_sent_row), tone = Kit.Violet)
                else -> InviteChip(stringResource(R.string.friends_invite_button), enabled = !busy, onClick = onInvite)
            }
        },
    )
}

@Composable
private fun InviteChip(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(50))
            .background(Kit.Primary)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { NText(text, 13.sp, weight = FontWeight.ExtraBold, maxLines = 1) }
}

@Composable
private fun PersonRow(name: String, username: String, trailing: (@Composable () -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KitAvatar(name)
        Column(Modifier.weight(1f)) {
            NText(name, 15.sp, weight = FontWeight.ExtraBold, maxLines = 1)
            NText("@$username", 12.sp, color = Neon.Muted, maxLines = 1)
        }
        trailing?.invoke()
    }
}

/** Invites | Log | Dost. The invite count shows on the first tab, so a waiting invite is never missed. */
@Composable
private fun FriendsTabs(selected: FriendsTab, invites: Int, onTab: (FriendsTab) -> Unit) {
    val shape = RoundedCornerShape(50)
    val labels = listOf(
        FriendsTab.INVITES to stringResource(R.string.tab_invites),
        FriendsTab.PEOPLE to stringResource(R.string.tab_people),
        FriendsTab.FRIENDS to stringResource(R.string.tab_friends),
    )
    Row(
        modifier = Modifier.fillMaxWidth().clip(shape).background(Kit.Surface).border(1.dp, Kit.Edge, shape).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEach { (tab, label) ->
            val on = tab == selected
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 46.dp)
                    .clip(shape)
                    .then(if (on) Modifier.background(Kit.Primary) else Modifier)
                    .clickable(role = Role.Tab) { onTab(tab) }
                    .semantics { this.selected = on },
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NText(label, 14.sp, color = if (on) Color.White else Neon.Muted, weight = FontWeight.ExtraBold, maxLines = 1)
                if (tab == FriendsTab.INVITES && invites > 0) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(if (on) Color.White else Kit.Pink), contentAlignment = Alignment.Center) {
                        NText(invites.coerceAtMost(9).toString(), 12.sp, color = if (on) Kit.VioletDeep else Color.White, weight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun InvitesTab(
    ui: FriendsUi,
    onAccept: (IncomingInvite) -> Unit,
    onDecline: (IncomingInvite) -> Unit,
    onAcceptChallenge: (DuelInfo) -> Unit,
    onDeclineChallenge: (DuelInfo) -> Unit,
    onJoinBroadcast: (BroadcastInvite) -> Unit,
    onDismissBroadcast: (BroadcastInvite) -> Unit,
) {
    if (ui.incoming.isEmpty() && ui.challenges.isEmpty() && ui.broadcastInvites.isEmpty()) {
        EmptyNote(R.string.invites_empty)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ui.broadcastInvites.forEach { invite ->
            KitCard(
                radius = 22.dp,
                fill = Brush.horizontalGradient(listOf(Kit.Green.copy(alpha = 0.18f), Kit.Surface)),
                edge = Kit.Green.copy(alpha = 0.4f),
                padding = PaddingValues(14.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KitAvatar(invite.fromName, tone = Kit.Green)
                    Column(Modifier.weight(1f)) {
                        NText(stringResource(R.string.friends_bc_invite_title, invite.fromName), 15.sp, weight = FontWeight.ExtraBold, lineHeight = 20.sp)
                        NText(invite.title, 12.sp, color = Neon.Muted, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KitButton(stringResource(R.string.invites_decline), { onDismissBroadcast(invite) }, kind = KitButtonKind.Secondary, modifier = Modifier.weight(1f))
                    KitButton(stringResource(R.string.bc_join), { onJoinBroadcast(invite) }, icon = com.doomscrollduel.feature.common.KitIcons.Broadcast, modifier = Modifier.weight(1f))
                }
            }
        }
        ui.challenges.forEach { duel ->
            val busy = ui.answeringDuel == duel.id
            val time = stringResource(
                when (duel.hours) {
                    6 -> R.string.nd_duration_6h
                    168 -> R.string.nd_duration_7d
                    else -> R.string.nd_duration_24h
                },
            )
            KitCard(
                radius = 22.dp,
                fill = Brush.horizontalGradient(listOf(Kit.Gold.copy(alpha = 0.18f), Kit.Surface)),
                edge = Kit.Gold.copy(alpha = 0.4f),
                padding = PaddingValues(14.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KitAvatar(duel.challengerName, tone = Kit.Gold)
                    Column(Modifier.weight(1f)) {
                        NText(stringResource(R.string.friends_challenge_title, duel.challengerName), 15.sp, weight = FontWeight.ExtraBold, lineHeight = 20.sp)
                        NText(stringResource(R.string.friends_challenge_detail, duel.reelLimit, time, duel.stakeCoins), 12.sp, color = Neon.Muted, lineHeight = 16.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KitButton(stringResource(R.string.invites_decline), { onDeclineChallenge(duel) }, kind = KitButtonKind.Secondary, enabled = !busy, modifier = Modifier.weight(1f))
                    KitButton(stringResource(R.string.invites_accept), { onAcceptChallenge(duel) }, enabled = !busy, icon = NeonIcons.Swords, modifier = Modifier.weight(1f))
                }
            }
        }
        ui.incoming.forEach { invite ->
            val busy = ui.answeringUid == invite.fromUid
            KitCard(radius = 22.dp, padding = PaddingValues(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KitAvatar(invite.fromName, tone = Kit.Pink)
                    Column(Modifier.weight(1f)) {
                        NText(invite.fromName, 16.sp, weight = FontWeight.ExtraBold, maxLines = 1)
                        NText(stringResource(R.string.invites_from, invite.fromUsername), 12.sp, color = Neon.Muted, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KitButton(stringResource(R.string.invites_decline), { onDecline(invite) }, kind = KitButtonKind.Secondary, enabled = !busy, modifier = Modifier.weight(1f))
                    KitButton(stringResource(R.string.invites_accept), { onAccept(invite) }, enabled = !busy, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Everybody who signed in, with a search box. Each row has an INVITE button, or says why not. */
@Composable
private fun PeopleTab(ui: FriendsUi, onSearch: (String) -> Unit, onInvite: (String) -> Unit) {
    KitField(
        value = ui.search,
        onValueChange = onSearch,
        label = stringResource(R.string.people_search_label),
        hint = stringResource(R.string.people_search_hint),
    )
    Spacer(Modifier.height(14.dp))
    when {
        ui.peopleTotal == 0 -> EmptyNote(R.string.people_empty)
        ui.people.isEmpty() -> EmptyNote(R.string.people_none_found)
        else -> KitCard(padding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
            ui.people.forEachIndexed { i, person ->
                if (i > 0) KitDivider()
                PersonRow(
                    name = person.displayName,
                    username = person.username,
                    trailing = {
                        when {
                            person.isFriend -> KitPill(stringResource(R.string.friends_is_friend), tone = Kit.Green)
                            person.invited -> KitPill(stringResource(R.string.people_invited), tone = Kit.Violet)
                            else -> InviteChip(stringResource(R.string.friends_invite_button), enabled = ui.invitingUid == null, onClick = { onInvite(person.uid) })
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyNote(textRes: Int) {
    NText(stringResource(textRes), 14.sp, color = Neon.Muted, lineHeight = 20.sp, modifier = Modifier.padding(vertical = 8.dp))
}

@Preview(name = "Friends", widthDp = 390, heightDp = 900)
@Composable
private fun FriendsPreview() = ScreenPreview {
    FriendsScreen(
        ui = FriendsUi(
            me = Profile("1", "vikas_07", "Vikas"),
            signedIn = true,
            friends = listOf(Friend("2", "rohan_k", "Rohan")),
            contacts = ContactsUi.Found(listOf(ContactMatch("3", "simran", "Simran", false), ContactMatch("2", "rohan_k", "Rohan", true))),
        ),
        onBack = {}, onLogin = {}, onShareLink = {}, onAddText = {}, onAddFriend = {}, onAllowContacts = {},
        onReloadContacts = {}, onInvite = {}, onNoticeDone = {},
    )
}
