package com.doomscrollduel.feature.friends

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.doomscrollduel.R
import com.doomscrollduel.domain.model.InviteCode
import com.doomscrollduel.domain.repository.AcceptInviteResult
import com.doomscrollduel.domain.repository.FriendsRepository
import com.doomscrollduel.feature.auth.SessionPrefs
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.doomscrollduel.feature.common.Kit
import com.doomscrollduel.feature.common.KitBottomButton
import com.doomscrollduel.feature.common.KitButtonKind
import com.doomscrollduel.feature.common.KitCard
import com.doomscrollduel.feature.common.KitIconDisc
import com.doomscrollduel.feature.common.KitPage
import com.doomscrollduel.feature.settings.neon.NText
import com.doomscrollduel.feature.settings.neon.NeonIcons

sealed interface InviteAcceptUi {
    data object Working : InviteAcceptUi
    data object NeedsLogin : InviteAcceptUi
    data class Done(val username: String) : InviteAcceptUi
    data object AlreadyFriends : InviteAcceptUi
    data class Problem(@StringRes val text: Int) : InviteAcceptUi
}

/** Opens from an invite link or from the "Join karo" notification, and accepts the code in it. */
@HiltViewModel
class InviteAcceptViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val friends: FriendsRepository,
    private val prefs: SessionPrefs,
) : ViewModel() {
    private val _ui = MutableStateFlow<InviteAcceptUi>(InviteAcceptUi.Working)
    val ui: StateFlow<InviteAcceptUi> = _ui

    init {
        val code = InviteCode.parse(handle.get<String>(ARG).orEmpty())
        when {
            code == null -> _ui.value = InviteAcceptUi.Problem(R.string.invite_accept_bad)
            FirebaseAuth.getInstance().currentUser == null -> {
                // Remembered, so the invite is taken as soon as the person has logged in and chosen a username.
                prefs.pendingInvite = code.value
                _ui.value = InviteAcceptUi.NeedsLogin
            }
            else -> viewModelScope.launch {
                _ui.value = when (val r = friends.acceptInvite(code)) {
                    is AcceptInviteResult.Accepted -> InviteAcceptUi.Done(r.friend.username)
                    AcceptInviteResult.AlreadyFriends -> InviteAcceptUi.AlreadyFriends
                    AcceptInviteResult.InvalidOrExpired -> InviteAcceptUi.Problem(R.string.invite_accept_bad)
                    AcceptInviteResult.NoNetwork -> InviteAcceptUi.Problem(R.string.friends_network)
                    AcceptInviteResult.NotSignedIn -> InviteAcceptUi.NeedsLogin
                    AcceptInviteResult.Failed -> InviteAcceptUi.Problem(R.string.friends_failed)
                }
                // Done with this invite, whatever the answer. Only "no internet" and "not logged in" keep it for later.
                val keep = _ui.value == InviteAcceptUi.NeedsLogin || _ui.value == InviteAcceptUi.Problem(R.string.friends_network)
                if (!keep) prefs.pendingInvite = null
            }
        }
    }

    companion object {
        const val ARG = "code"
    }
}

@Composable
fun InviteAcceptRoute(
    onHome: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InviteAcceptViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    // Logged out: send the person to the login page; the saved invite is taken afterwards.
    LaunchedEffect(ui) { if (ui == InviteAcceptUi.NeedsLogin) onLogin() }
    InviteAcceptScreen(ui = ui, onHome = onHome, modifier = modifier)
}

@Composable
fun InviteAcceptScreen(ui: InviteAcceptUi, onHome: () -> Unit, modifier: Modifier = Modifier) {
    val good = ui is InviteAcceptUi.Done || ui is InviteAcceptUi.AlreadyFriends
    val bad = ui is InviteAcceptUi.Problem
    val tone = when {
        good -> Kit.Green
        bad -> Kit.Red
        else -> Kit.Violet
    }
    val text = when (ui) {
        InviteAcceptUi.Working -> stringResource(R.string.invite_accept_working)
        InviteAcceptUi.NeedsLogin -> stringResource(R.string.invite_accept_login)
        is InviteAcceptUi.Done -> stringResource(R.string.invite_accept_done, ui.username)
        InviteAcceptUi.AlreadyFriends -> stringResource(R.string.invite_accept_already)
        is InviteAcceptUi.Problem -> stringResource(ui.text)
    }
    KitPage(
        modifier = modifier,
        bottom = {
            if (ui !is InviteAcceptUi.Working) {
                KitBottomButton(
                    text = stringResource(R.string.invite_accept_home),
                    onClick = onHome,
                    kind = if (bad) KitButtonKind.Secondary else KitButtonKind.Primary,
                )
            }
        },
    ) {
        Spacer(Modifier.height(48.dp))
        NText(
            text = stringResource(R.string.invite_accept_title),
            size = 30.sp,
            weight = FontWeight.Black,
            align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        )
        KitCard(
            radius = 26.dp,
            fill = Brush.horizontalGradient(listOf(tone.copy(alpha = 0.18f), tone.copy(alpha = 0.18f))),
            edge = tone.copy(alpha = 0.45f),
            padding = PaddingValues(24.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                KitIconDisc(if (bad) NeonIcons.Shield else NeonIcons.People, tone, size = 64.dp)
                NText(text, 20.sp, weight = FontWeight.ExtraBold, align = TextAlign.Center, lineHeight = 27.sp, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
