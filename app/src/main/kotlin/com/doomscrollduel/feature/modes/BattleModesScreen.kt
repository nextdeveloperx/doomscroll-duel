package com.doomscrollduel.feature.modes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.doomscrollduel.R
import com.doomscrollduel.core.designsystem.brain.BrainOwner
import com.doomscrollduel.core.designsystem.brain.BrainState
import com.doomscrollduel.core.designsystem.brain.BrainView
import com.doomscrollduel.core.designsystem.components.ChunkySurface
import com.doomscrollduel.core.designsystem.components.DuelIcons
import com.doomscrollduel.core.designsystem.components.DuelScreen
import com.doomscrollduel.core.designsystem.components.DuelTab
import com.doomscrollduel.core.designsystem.components.DuelText
import com.doomscrollduel.core.designsystem.components.ModeTag
import com.doomscrollduel.core.designsystem.components.ModeTagPill
import com.doomscrollduel.core.designsystem.components.ModeTile
import com.doomscrollduel.core.designsystem.components.TabbedScreenPreview
import com.doomscrollduel.core.designsystem.components.VsBadge
import com.doomscrollduel.core.designsystem.components.scaled
import com.doomscrollduel.core.designsystem.theme.ChunkyMetrics
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import com.doomscrollduel.domain.billing.ProFeature
import com.doomscrollduel.feature.paywall.UpgradePrompt

enum class BattleMode { DUEL, SQUAD, NIGHT_PACT, FORFEIT_DARE, STRICT_LOCK }

/** The Pro feature a mode needs, or null when the mode is free. Agrees with `ProGate` in the domain. */
fun BattleMode.requiredFeature(): ProFeature? = when (this) {
    BattleMode.SQUAD -> ProFeature.SQUAD_BATTLE
    BattleMode.STRICT_LOCK -> ProFeature.STRICT_LOCK
    BattleMode.DUEL, BattleMode.NIGHT_PACT, BattleMode.FORFEIT_DARE -> null
}

/**
 * [upgradePrompt] is set when a non-Pro person tapped a Pro tile. The prompt sits at the bottom, over nothing: the
 * tiles stay usable and "Baad mein" closes it.
 */
@Composable
fun BattleModesScreen(
    onModeSelected: (BattleMode) -> Unit,
    modifier: Modifier = Modifier,
    upgradePrompt: ProFeature? = null,
    onSeePro: () -> Unit = {},
    onDismissPrompt: () -> Unit = {},
) {
    val colors = DuelTheme.colors
    DuelScreen(
        modifier = modifier,
        bottom = upgradePrompt?.let { feature -> { UpgradePrompt(feature, onSeePro = onSeePro, onLater = onDismissPrompt) } },
    ) {
        DuelText(
            text = stringResource(R.string.modes_title),
            style = DuelTheme.typography.title,
            modifier = Modifier.padding(top = 20.dp, bottom = 16.dp),
        )
        DuelHeroTile(onClick = { onModeSelected(BattleMode.DUEL) })
        Spacer(Modifier.height(20.dp))
        ModeRow {
            ModeTile(
                name = stringResource(R.string.mode_squad_name),
                description = stringResource(R.string.mode_squad_desc),
                icon = DuelIcons.Friends,
                color = colors.cyan,
                tag = ModeTag.PRO,
                onClick = { onModeSelected(BattleMode.SQUAD) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            ModeTile(
                name = stringResource(R.string.mode_night_name),
                description = stringResource(R.string.mode_night_desc),
                icon = DuelIcons.Moon,
                color = colors.lavender,
                tag = ModeTag.FREE,
                onClick = { onModeSelected(BattleMode.NIGHT_PACT) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
        Spacer(Modifier.height(16.dp))
        ModeRow {
            ModeTile(
                name = stringResource(R.string.mode_dare_name),
                description = stringResource(R.string.mode_dare_desc),
                icon = DuelIcons.Flame,
                color = colors.pink,
                tag = ModeTag.FREE,
                onClick = { onModeSelected(BattleMode.FORFEIT_DARE) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            ModeTile(
                name = stringResource(R.string.mode_lock_name),
                description = stringResource(R.string.mode_lock_desc),
                icon = DuelIcons.Lock,
                color = colors.orange,
                tag = ModeTag.PRO,
                onClick = { onModeSelected(BattleMode.STRICT_LOCK) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Two equal-height tiles side by side. */
@Composable
private fun ModeRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

/** Big yellow 1v1 tile with both brains. */
@Composable
private fun DuelHeroTile(onClick: () -> Unit) {
    val colors = DuelTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val name = stringResource(R.string.mode_duel_name)
    val description = stringResource(R.string.mode_duel_desc)
    val tagText = stringResource(R.string.ds_tag_free)
    val spoken = stringResource(R.string.ds_mode_tile_description, name, description, tagText)

    ChunkySurface(
        shape = DuelTheme.shapes.tile,
        fill = colors.yellow,
        pressed = pressed,
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = ChunkyMetrics.MinTouchTarget)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = spoken },
    ) {
        Column(
            modifier = Modifier
                .clearAndSetSemantics { }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                BrainView(BrainState.HAPPY, Modifier.width(112.dp.scaled()), BrainOwner.YOU)
                VsBadge(Modifier.padding(horizontal = 4.dp))
                BrainView(BrainState.HAPPY, Modifier.width(112.dp.scaled()), BrainOwner.OPPONENT)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                DuelText(text = name, style = DuelTheme.typography.title, color = colors.onBright)
                ModeTagPill(tag = ModeTag.FREE, text = tagText)
            }
            DuelText(text = description, style = DuelTheme.typography.body, color = colors.onBright)
        }
    }
}

@Preview(name = "Battle modes 390x844", widthDp = 390, heightDp = 844)
@Composable
private fun BattleModesPreview() = TabbedScreenPreview(DuelTab.BATTLES) {
    BattleModesScreen(onModeSelected = {})
}

@Preview(name = "Battle modes small 320x640", widthDp = 320, heightDp = 640)
@Composable
private fun BattleModesSmallPreview() = TabbedScreenPreview(DuelTab.BATTLES) {
    BattleModesScreen(onModeSelected = {})
}
