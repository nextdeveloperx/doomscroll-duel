package com.doomscrollduel.feature.paywall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.doomscrollduel.R
import com.doomscrollduel.core.designsystem.components.ChunkyButton
import com.doomscrollduel.core.designsystem.components.ChunkyButtonStyle
import com.doomscrollduel.core.designsystem.components.ChunkyCard
import com.doomscrollduel.core.designsystem.components.DuelText
import com.doomscrollduel.core.designsystem.components.ScreenPreview
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import com.doomscrollduel.domain.billing.ProFeature

@Composable
fun proFeatureReason(feature: ProFeature): String = stringResource(
    when (feature) {
        ProFeature.UNLIMITED_DUELS -> R.string.paywall_trigger_unlimited_duels
        ProFeature.SQUAD_BATTLE -> R.string.paywall_trigger_squad
        ProFeature.STRICT_LOCK -> R.string.paywall_trigger_strict_lock
        ProFeature.ANALYTICS -> R.string.paywall_trigger_analytics
        ProFeature.CUSTOM_SCHEDULES -> R.string.paywall_trigger_schedules
        ProFeature.BRAIN_SKINS -> R.string.paywall_trigger_skins
    },
)

/**
 * The soft prompt shown when someone taps a Pro thing. It is a card, not a wall: "Baad mein" is as big as "Pro dekho",
 * nothing is locked behind it, and it never appears on its own, only after a tap.
 */
@Composable
fun UpgradePrompt(
    feature: ProFeature,
    onSeePro: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DuelTheme.colors
    ChunkyCard(modifier = modifier.fillMaxWidth(), fill = colors.yellow) {
        DuelText(text = proFeatureReason(feature), style = DuelTheme.typography.bodyStrong, color = colors.onBright)
        Spacer(Modifier.height(4.dp))
        DuelText(text = stringResource(R.string.upgrade_prompt_free_note), style = DuelTheme.typography.caption, color = colors.onBright)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ChunkyButton(
                text = stringResource(R.string.upgrade_prompt_see),
                onClick = onSeePro,
                style = ChunkyButtonStyle.Secondary,
                modifier = Modifier.weight(1f),
            )
            ChunkyButton(
                text = stringResource(R.string.upgrade_prompt_later),
                onClick = onLater,
                style = ChunkyButtonStyle.Success,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 300)
@Composable
private fun UpgradePromptPreview() = ScreenPreview {
    Column(Modifier.fillMaxWidth()) { UpgradePrompt(ProFeature.SQUAD_BATTLE, {}, {}) }
}
