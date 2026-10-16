package com.doomscrollduel.feature.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.doomscrollduel.R
import com.doomscrollduel.domain.social.ProfileExtras
import com.doomscrollduel.domain.social.ProfileRepository
import com.doomscrollduel.domain.social.SocialApp
import com.doomscrollduel.feature.common.Kit
import com.doomscrollduel.feature.common.KitAvatar
import com.doomscrollduel.feature.common.KitButton
import com.doomscrollduel.feature.common.KitButtonKind
import com.doomscrollduel.feature.common.KitCard
import com.doomscrollduel.feature.settings.neon.NText
import com.doomscrollduel.feature.settings.neon.Neon
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@StringRes
private fun SocialApp.label(): Int = when (this) {
    SocialApp.INSTAGRAM -> R.string.social_instagram
    SocialApp.YOUTUBE -> R.string.social_youtube
    SocialApp.SNAPCHAT -> R.string.social_snapchat
    SocialApp.FACEBOOK -> R.string.social_facebook
}

/** The bio, then one chip per social handle the person added; a chip opens that profile in the browser or the app. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileExtrasBlock(extras: ProfileExtras, modifier: Modifier = Modifier, emptyText: String? = null) {
    val uri = LocalUriHandler.current
    val links = SocialApp.entries.mapNotNull { app -> app.url(extras.handle(app))?.let { app to it } }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (extras.bio.isNotBlank()) {
            NText(extras.bio, 14.sp, lineHeight = 20.sp, align = TextAlign.Center)
        } else if (emptyText != null && links.isEmpty()) {
            NText(emptyText, 13.sp, color = Neon.Muted, align = TextAlign.Center)
        }
        if (links.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                links.forEach { (app, url) ->
                    val shape = RoundedCornerShape(50)
                    Column(
                        Modifier
                            .heightIn(min = 44.dp)
                            .clip(shape)
                            .background(Kit.Surface)
                            .border(1.dp, Kit.Violet.copy(alpha = 0.6f), shape)
                            .clickable(role = Role.Button) { runCatching { uri.openUri(url) } }
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.Center,
                    ) { NText(stringResource(app.label()), 13.sp, weight = FontWeight.ExtraBold, maxLines = 1) }
                }
            }
        }
    }
}

@HiltViewModel
class PersonProfileViewModel @Inject constructor(private val profiles: ProfileRepository) : ViewModel() {
    sealed interface State {
        data object Loading : State
        data object Failed : State
        data class Ready(val extras: ProfileExtras) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state
    private var loadedFor: String? = null

    fun load(uid: String) {
        if (loadedFor == uid) return
        loadedFor = uid
        _state.value = State.Loading
        viewModelScope.launch {
            val extras = profiles.extrasOf(uid)
            _state.value = if (extras == null) State.Failed else State.Ready(extras)
        }
    }
}

/** Another person's public card: name, @username, bio and social chips. Opened by tapping them in the Dost or Log list. */
@Composable
fun PersonProfileDialog(
    uid: String,
    name: String,
    username: String,
    onDismiss: () -> Unit,
    viewModel: PersonProfileViewModel = hiltViewModel(),
) {
    LaunchedEffect(uid) { viewModel.load(uid) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Dialog(onDismissRequest = onDismiss) {
        KitCard(radius = 26.dp, padding = PaddingValues(20.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                KitAvatar(name, size = 80.dp)
                Spacer(Modifier.height(10.dp))
                NText(name, 20.sp, weight = FontWeight.ExtraBold, align = TextAlign.Center)
                NText("@$username", 14.sp, color = Neon.VioletLight, weight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.height(14.dp))
                when (val s = state) {
                    PersonProfileViewModel.State.Loading -> NText(stringResource(R.string.profile_loading), 13.sp, color = Neon.Muted)
                    PersonProfileViewModel.State.Failed -> NText(stringResource(R.string.person_load_failed), 13.sp, color = Neon.Orange, align = TextAlign.Center)
                    is PersonProfileViewModel.State.Ready -> ProfileExtrasBlock(s.extras, emptyText = stringResource(R.string.person_no_info))
                }
                Spacer(Modifier.height(18.dp))
                KitButton(stringResource(R.string.person_close), onDismiss, kind = KitButtonKind.Secondary)
            }
        }
    }
}
