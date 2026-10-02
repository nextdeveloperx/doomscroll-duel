package com.doomscrollduel.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doomscrollduel.R
import com.doomscrollduel.core.designsystem.components.DuelIcon
import com.doomscrollduel.core.designsystem.theme.Nunito
import com.doomscrollduel.domain.social.Profile
import com.doomscrollduel.domain.social.ProfileState
import com.doomscrollduel.feature.common.Kit
import com.doomscrollduel.feature.common.KitButton
import com.doomscrollduel.feature.common.KitButtonKind
import com.doomscrollduel.feature.common.KitCard
import com.doomscrollduel.feature.common.KitIconDisc
import com.doomscrollduel.feature.common.KitPage
import com.doomscrollduel.feature.common.KitUsernamePrompt
import com.doomscrollduel.feature.modes.BlueHot
import com.doomscrollduel.feature.modes.GoldHot
import com.doomscrollduel.feature.modes.PinkHot
import com.doomscrollduel.feature.modes.VioletHot
import com.doomscrollduel.feature.settings.neon.NText
import com.doomscrollduel.feature.settings.neon.Neon
import com.doomscrollduel.feature.settings.neon.NeonIcons

@Composable
fun ProfileRoute(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenSettings: () -> Unit,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier,
    onChooseUsername: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    ProfileScreen(
        ui = ui,
        onBack = onBack,
        onLogin = onLogin,
        onOpenFriends = onOpenFriends,
        onOpenSettings = onOpenSettings,
        onEdit = viewModel::startEdit,
        onDraft = viewModel::onDraft,
        onSaveName = viewModel::saveName,
        onCancelEdit = viewModel::cancelEdit,
        onAskLogout = viewModel::askLogout,
        onCancelLogout = viewModel::cancelLogout,
        onLogout = { viewModel.logout(onLoggedOut) },
        modifier = modifier,
        onChooseUsername = onChooseUsername,
    )
}

/** Who I am: avatar, name, @username, email; change the name; go to Dost or Settings; log out. Guests see a login card. */
@Composable
fun ProfileScreen(
    ui: ProfileUi,
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit,
    onLogin: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenSettings: () -> Unit,
    onEdit: (String) -> Unit,
    onDraft: (String) -> Unit,
    onSaveName: () -> Unit,
    onCancelEdit: () -> Unit,
    onAskLogout: () -> Unit,
    onCancelLogout: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    onChooseUsername: () -> Unit = {},
) {
    val profile = (ui.state as? ProfileState.Ready)?.profile
    val signedIn = ui.state != ProfileState.SignedOut
    KitPage(modifier = modifier, title = stringResource(R.string.profile_title)) {
        if (!signedIn) {
            GuestPanel(onLogin)
            return@KitPage
        }

        // ----- who -----
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val name = profile?.displayName ?: ui.email ?: "?"
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(PinkHot, Kit.VioletDeep))),
                contentAlignment = Alignment.Center,
            ) { NText(name.trim().firstOrNull()?.uppercase() ?: "?", 40.sp, weight = FontWeight.ExtraBold) }
            Spacer(Modifier.height(12.dp))
            NText(
                profile?.displayName ?: stringResource(R.string.profile_loading), 22.sp,
                weight = FontWeight.ExtraBold, maxLines = 1, align = TextAlign.Center,
            )
            if (profile != null) {
                NText("@" + profile.username, 15.sp, color = Neon.VioletLight, weight = FontWeight.Bold, maxLines = 1)
            } else if (ui.state == ProfileState.Unavailable) {
                NText(stringResource(R.string.profile_unavailable), 13.sp, color = Neon.Orange, align = TextAlign.Center)
            }
            ui.email?.let { NText(it, 12.sp, color = Neon.Muted, maxLines = 1) }
        }

        if (ui.state == ProfileState.NeedsUsername) {
            Spacer(Modifier.height(18.dp))
            KitUsernamePrompt(
                title = stringResource(R.string.need_username_title),
                body = stringResource(R.string.need_username_body),
                button = stringResource(R.string.need_username_button),
                onChoose = onChooseUsername,
            )
        }

        // ----- change name -----
        if (profile != null) {
            Spacer(Modifier.height(18.dp))
            if (ui.editing) {
                NeonField(ui.draftName, onDraft, stringResource(R.string.profile_name_label), enabled = !ui.saving)
                ui.message?.let {
                    Spacer(Modifier.height(8.dp))
                    NText(stringResource(it), 13.sp, color = Neon.Red, weight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(stringResource(R.string.profile_cancel), onCancelEdit, Modifier.weight(1f), filled = false)
                    PillButton(stringResource(R.string.profile_save), onSaveName, Modifier.weight(1f), enabled = ui.draftName.isNotBlank() && !ui.saving)
                }
            } else {
                RowLink(NeonIcons.Person, stringResource(R.string.profile_edit_name), PinkHot) { onEdit(profile.displayName) }
            }
        }

        // ----- go elsewhere -----
        Spacer(Modifier.height(12.dp))
        RowLink(NeonIcons.People, stringResource(R.string.profile_friends), BlueHot, onOpenFriends)
        Spacer(Modifier.height(10.dp))
        RowLink(NeonIcons.Gear, stringResource(R.string.profile_settings), VioletHot, onOpenSettings)

        // ----- logout -----
        Spacer(Modifier.height(26.dp))
        if (ui.askLogout) {
            KitCard(edge = Neon.Red.copy(alpha = 0.45f), padding = PaddingValues(16.dp)) {
                NText(stringResource(R.string.profile_logout_ask), 18.sp, weight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
                NText(stringResource(R.string.profile_logout_ask_sub), 13.sp, color = Neon.Muted, lineHeight = 18.sp)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(stringResource(R.string.profile_cancel), onCancelLogout, Modifier.weight(1f), filled = false, enabled = !ui.loggingOut)
                    PillButton(
                        stringResource(if (ui.loggingOut) R.string.profile_logging_out else R.string.profile_logout_yes),
                        onLogout, Modifier.weight(1f), enabled = !ui.loggingOut, tone = Neon.Red,
                    )
                }
            }
        } else {
            KitButton(stringResource(R.string.profile_logout), onAskLogout, kind = KitButtonKind.Danger)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun GuestPanel(onLogin: () -> Unit) {
    KitCard(fill = Brush.horizontalGradient(listOf(GoldHot.copy(alpha = 0.18f), Kit.Surface)), edge = GoldHot.copy(alpha = 0.35f), padding = PaddingValues(18.dp)) {
        NText(stringResource(R.string.profile_guest_title), 20.sp, weight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        NText(stringResource(R.string.profile_guest_sub), 13.sp, color = Neon.Muted, lineHeight = 18.sp)
        Spacer(Modifier.height(14.dp))
        KitButton(stringResource(R.string.profile_login), onLogin)
    }
}

@Composable
private fun RowLink(icon: ImageVector, label: String, accent: Color, onClick: () -> Unit) {
    KitCard(radius = 20.dp, padding = PaddingValues(horizontal = 14.dp, vertical = 6.dp), onClick = onClick) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KitIconDisc(icon, accent, size = 36.dp)
            NText(label, 15.sp, weight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.weight(1f))
            DuelIcon(NeonIcons.ChevronRight, tint = Neon.Muted, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
    tone: Color = PinkHot,
) {
    val pill = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(pill)
            .then(
                if (filled) Modifier.background(Brush.horizontalGradient(listOf(tone, Kit.VioletDeep)))
                else Modifier.background(Kit.SurfaceHigh).border(1.dp, Kit.Edge, pill),
            )
            .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { NText(text, 14.sp, weight = FontWeight.ExtraBold, maxLines = 1, color = if (enabled) Color.White else Neon.Muted) }
}

@Composable
private fun NeonField(value: String, onChange: (String) -> Unit, label: String, enabled: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxWidth()) {
        NText(label, 12.sp, color = Neon.VioletLight, weight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(shape)
                .background(Kit.Surface)
                .border(1.dp, Kit.Violet.copy(alpha = 0.6f), shape)
                .padding(horizontal = 14.dp, vertical = 14.dp),
        )
    }
}

@Preview(name = "Profile", widthDp = 390, heightDp = 844)
@Composable
private fun ProfilePreview() {
    ProfileScreen(
        ui = ProfileUi(state = ProfileState.Ready(Profile("1", "vikas_07", "Vikas Kumar")), email = "vikas@gmail.com"),
        onBack = {}, onLogin = {}, onOpenFriends = {}, onOpenSettings = {}, onEdit = {}, onDraft = {}, onSaveName = {},
        onCancelEdit = {}, onAskLogout = {}, onCancelLogout = {}, onLogout = {},
    )
}
