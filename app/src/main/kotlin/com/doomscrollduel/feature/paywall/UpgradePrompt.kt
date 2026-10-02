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
import com.doomscrollduel.core.designsystem.components.ScreenPreview
import com.doomscrollduel.domain.billing.ProFeature
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.doomscrollduel.core.designsystem.components.ScreenPreview
import com.doomscrollduel.feature.common.Kit
import com.doomscrollduel.feature.common.KitButton
import com.doomscrollduel.feature.common.KitButtonKind
import com.doomscrollduel.feature.common.KitCard
import com.doomscrollduel.feature.common.KitIconDisc
import com.doomscrollduel.feature.settings.neon.NText
import com.doomscrollduel.feature.settings.neon.Neon
import com.doomscrollduel.feature.settings.neon.NeonIcons

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
    KitCard(
        modifier = modifier,
        radius = 26.dp,
        fill = Brush.verticalGradient(listOf(Kit.SurfaceHigh, Kit.SurfaceHigh)),
        edge = Kit.Gold.copy(alpha = 0.5f),
        padding = PaddingValues(18.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KitIconDisc(NeonIcons.Crown, Kit.Gold, size = 40.dp)
            Column(Modifier.weight(1f)) {
                NText(proFeatureReason(feature), 15.sp, weight = FontWeight.ExtraBold, lineHeight = 21.sp)
                Spacer(Modifier.height(4.dp))
                NText(stringResource(R.string.upgrade_prompt_free_note), 12.sp, color = Neon.Muted, lineHeight = 16.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            KitButton(stringResource(R.string.upgrade_prompt_later), onLater, kind = KitButtonKind.Secondary, modifier = Modifier.weight(1f))
            KitButton(stringResource(R.string.upgrade_prompt_see), onSeePro, modifier = Modifier.weight(1f))
        }
    }
}

@Preview(widthDp = 390, heightDp = 300)
@Composable
private fun UpgradePromptPreview() = ScreenPreview {
    Column(Modifier.fillMaxWidth()) { UpgradePrompt(ProFeature.SQUAD_BATTLE, {}, {}) }
}
