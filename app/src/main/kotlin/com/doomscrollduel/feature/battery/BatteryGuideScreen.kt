package com.doomscrollduel.feature.battery

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doomscrollduel.R
import com.doomscrollduel.core.common.openBatterySettings
import com.doomscrollduel.core.common.openOemAutostartSettings
import com.doomscrollduel.core.designsystem.theme.ChunkyMetrics
import com.doomscrollduel.tracking.health.BatteryGuide
import com.doomscrollduel.tracking.health.BatteryStep
import com.doomscrollduel.tracking.health.OemFamily
import com.doomscrollduel.tracking.health.TrackingHealthViewModel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.doomscrollduel.core.designsystem.components.ScreenPreview
import com.doomscrollduel.feature.common.Kit
import com.doomscrollduel.feature.common.KitButton
import com.doomscrollduel.feature.common.KitButtonKind
import com.doomscrollduel.feature.common.KitCard
import com.doomscrollduel.feature.common.KitIconDisc
import com.doomscrollduel.feature.common.KitPage
import com.doomscrollduel.feature.common.KitPill
import com.doomscrollduel.feature.settings.neon.NText
import com.doomscrollduel.feature.settings.neon.Neon
import com.doomscrollduel.feature.settings.neon.NeonIcons

@Composable
fun BatteryGuideRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrackingHealthViewModel = hiltViewModel(),
) {
    val health by viewModel.health.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val oem = OemFamily.from(Build.MANUFACTURER, Build.BRAND)

    // Re-check when the user comes back from the system screens.
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    BatteryGuideScreen(
        oem = oem,
        batteryUnrestricted = health.batteryUnrestricted,
        onBack = onBack,
        onOpenBatterySettings = { context.openBatterySettings() },
        onOpenOemSettings = { context.openOemAutostartSettings(oem) },
        modifier = modifier,
    )
}

@Composable
fun BatteryGuideScreen(
    oem: OemFamily,
    batteryUnrestricted: Boolean,
    onBack: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenOemSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val steps = BatteryGuide.stepsFor(oem)

    KitPage(
        modifier = modifier,
        title = stringResource(R.string.battery_title),
        onBack = onBack,
        bottomSpace = if (oem != OemFamily.OTHER) 170.dp else 92.dp,
        bottom = {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KitButton(stringResource(R.string.battery_open_settings), onOpenBatterySettings)
                if (oem != OemFamily.OTHER) {
                    KitButton(stringResource(R.string.battery_open_oem_settings), onOpenOemSettings, kind = KitButtonKind.Secondary)
                }
            }
        },
    ) {
        NText(stringResource(R.string.battery_intro), 15.sp, weight = FontWeight.Bold, lineHeight = 22.sp)
        Spacer(Modifier.height(12.dp))
        KitPill(stringResource(R.string.battery_phone_family, stringResource(oem.nameRes())), tone = Kit.Violet)
        Spacer(Modifier.height(16.dp))
        if (batteryUnrestricted) {
            KitCard(
                fill = Brush.horizontalGradient(listOf(Kit.Green.copy(alpha = 0.2f), Kit.Green.copy(alpha = 0.2f))),
                edge = Kit.Green.copy(alpha = 0.5f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KitIconDisc(NeonIcons.Check, Kit.Green, size = 36.dp)
                    NText(stringResource(R.string.battery_status_done), 16.sp, weight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        steps.forEachIndexed { index, step ->
            StepCard(number = index + 1, text = stringResource(step.textRes()))
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StepCard(number: Int, text: String) {
    val spoken = stringResource(R.string.battery_step_number, number) + ". " + text
    KitCard(radius = 22.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = spoken },
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Kit.Primary),
                contentAlignment = Alignment.Center,
            ) {
                NText(number.toString(), 16.sp, weight = FontWeight.Black)
            }
            NText(text, 14.sp, weight = FontWeight.Bold, lineHeight = 20.sp, modifier = Modifier.weight(1f).padding(top = 6.dp))
        }
    }
}

private fun OemFamily.nameRes(): Int = when (this) {
    OemFamily.XIAOMI -> R.string.oem_xiaomi
    OemFamily.REALME_OPPO -> R.string.oem_realme_oppo
    OemFamily.VIVO -> R.string.oem_vivo
    OemFamily.SAMSUNG -> R.string.oem_samsung
    OemFamily.OTHER -> R.string.oem_other
}

private fun BatteryStep.textRes(): Int = when (this) {
    BatteryStep.OPEN_BATTERY_LIST -> R.string.step_open_battery_list
    BatteryStep.PICK_APP_UNRESTRICTED -> R.string.step_pick_app_unrestricted
    BatteryStep.XIAOMI_AUTOSTART -> R.string.step_xiaomi_autostart
    BatteryStep.XIAOMI_BATTERY_SAVER -> R.string.step_xiaomi_battery_saver
    BatteryStep.REALME_AUTO_LAUNCH -> R.string.step_realme_auto_launch
    BatteryStep.REALME_BACKGROUND_ACTIVITY -> R.string.step_realme_background_activity
    BatteryStep.VIVO_AUTOSTART -> R.string.step_vivo_autostart
    BatteryStep.VIVO_BACKGROUND_POWER -> R.string.step_vivo_background_power
    BatteryStep.SAMSUNG_UNRESTRICTED -> R.string.step_samsung_unrestricted
    BatteryStep.SAMSUNG_SLEEPING_APPS -> R.string.step_samsung_sleeping_apps
    BatteryStep.SAMSUNG_UNUSED_APPS -> R.string.step_samsung_unused_apps
    BatteryStep.LOCK_IN_RECENTS -> R.string.step_lock_in_recents
}

@Preview(name = "Battery guide Xiaomi 390x844", widthDp = 390, heightDp = 844)
@Composable
private fun BatteryGuideXiaomiPreview() = ScreenPreview {
    BatteryGuideScreen(OemFamily.XIAOMI, batteryUnrestricted = false, onBack = {}, onOpenBatterySettings = {}, onOpenOemSettings = {})
}

@Preview(name = "Battery guide Samsung done 390x844", widthDp = 390, heightDp = 844)
@Composable
private fun BatteryGuideSamsungPreview() = ScreenPreview {
    BatteryGuideScreen(OemFamily.SAMSUNG, batteryUnrestricted = true, onBack = {}, onOpenBatterySettings = {}, onOpenOemSettings = {})
}
