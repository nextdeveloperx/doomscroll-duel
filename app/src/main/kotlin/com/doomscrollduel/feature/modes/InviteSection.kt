package com.doomscrollduel.feature.modes

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doomscrollduel.R
import com.doomscrollduel.core.designsystem.components.ChunkyCard
import com.doomscrollduel.core.designsystem.components.DuelIcon
import com.doomscrollduel.core.designsystem.components.DuelIcons
import com.doomscrollduel.core.designsystem.components.DuelText
import com.doomscrollduel.core.designsystem.theme.ChunkyMetrics
import com.doomscrollduel.core.designsystem.theme.DuelTheme

/** The kinds of friend a person can invite. Each has its own row and its own message. */
enum class InviteTarget(
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    @StringRes val message: Int,
) {
    NIGHT_OWL(R.string.invite_night_owl, R.string.invite_night_owl_sub, R.string.invite_message_night_owl),
    REEL_ADDICT(R.string.invite_reel_addict, R.string.invite_reel_addict_sub, R.string.invite_message_reel_addict),
    RIVAL(R.string.invite_rival, R.string.invite_rival_sub, R.string.invite_message_rival),
    ROOMMATE(R.string.invite_roommate, R.string.invite_roommate_sub, R.string.invite_message_roommate),
}

/**
 * "Dost ko bulao": one row per kind of friend, each with an INVITE button. Tapping it hands the message to
 * [onInvite], which opens the phone's share sheet. Nothing is sent without the person choosing a chat and sending.
 */
@Composable
fun InviteSection(onInvite: (InviteTarget) -> Unit, onOpenFriends: () -> Unit, modifier: Modifier = Modifier) {
    val colors = DuelTheme.colors
    val accents = listOf(colors.lavender, colors.cyan, colors.pink, colors.orange)
    Column(modifier = modifier.fillMaxWidth()) {
        DuelText(text = stringResource(R.string.invite_title), style = DuelTheme.typography.heading)
        Spacer(Modifier.height(2.dp))
        DuelText(text = stringResource(R.string.invite_sub), style = DuelTheme.typography.body, color = colors.textMuted)
        Spacer(Modifier.height(12.dp))
        // Real friends: the people in the phone's contacts who already have the app, with a push-notification invite.
        ChunkyCard(modifier = Modifier.fillMaxWidth(), fill = colors.cyan, onClick = onOpenFriends) {
            DuelText(text = stringResource(R.string.friends_open_from_battles), style = DuelTheme.typography.heading.copy(fontSize = 20.sp, lineHeight = 26.sp), color = colors.onBright)
            DuelText(text = stringResource(R.string.friends_open_from_battles_sub), style = DuelTheme.typography.body, color = colors.onBright)
        }
        Spacer(Modifier.height(12.dp))
        ChunkyCard(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
            InviteTarget.entries.forEachIndexed { index, target ->
                if (index > 0) Box(Modifier.fillMaxWidth().height(2.dp).background(colors.outline.copy(alpha = 0.55f)))
                InviteRow(target = target, accent = accents[index % accents.size], onInvite = { onInvite(target) })
            }
        }
    }
}

@Composable
private fun InviteRow(target: InviteTarget, accent: Color, onInvite: () -> Unit) {
    val colors = DuelTheme.colors
    val title = stringResource(target.title)
    val label = stringResource(R.string.invite_button) + ": " + title
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(accent)
                .border(ChunkyMetrics.OutlineWidth, colors.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            DuelIcon(DuelIcons.Friends, tint = colors.onBright, contentDescription = null, modifier = Modifier.size(24.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            DuelText(text = title, style = DuelTheme.typography.bodyStrong, maxLines = 1)
            DuelText(text = stringResource(target.subtitle), style = DuelTheme.typography.caption, color = colors.textMuted)
        }
        DuelText(
            text = stringResource(R.string.invite_button),
            style = DuelTheme.typography.captionStrong,
            color = colors.onBright,
            maxLines = 1,
            modifier = Modifier
                .heightIn(min = ChunkyMetrics.MinTouchTarget)
                .clip(DuelTheme.shapes.chip)
                .background(colors.yellow)
                .border(ChunkyMetrics.OutlineWidth, colors.outline, DuelTheme.shapes.chip)
                .clickable(role = Role.Button, onClick = onInvite)
                .semantics { contentDescription = label }
                .padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}
