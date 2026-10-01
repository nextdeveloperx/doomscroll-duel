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
import com.doomscrollduel.core.designsystem.components.BackButton
import com.doomscrollduel.core.designsystem.components.ChunkyButton
import com.doomscrollduel.core.designsystem.components.ChunkyButtonStyle
import com.doomscrollduel.core.designsystem.components.ChunkyCard
import com.doomscrollduel.core.designsystem.components.DuelScreen
import com.doomscrollduel.core.designsystem.components.DuelText
import com.doomscrollduel.core.designsystem.components.ScreenPreview
import com.doomscrollduel.core.designsystem.components.StatusPill
import com.doomscrollduel.core.designsystem.theme.ChunkyMetrics
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import com.doomscrollduel.tracking.health.BatteryGuide
import com.doomscrollduel.tracking.health.BatteryStep
import com.doomscrollduel.tracking.health.OemFamily
import com.doomscrollduel.tracking.health.TrackingHealthViewModel

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
    val colors = DuelTheme.colors
    val steps = BatteryGuide.stepsFor(oem)

    DuelScreen(
        modifier = modifier,
        bottom = {
            ChunkyButton(
                text = stringResource(R.string.battery_open_settings),
                onClick = onOpenBatterySettings,
                modifier = Modifier.fillMaxWidth(),
            )
            if (oem != OemFamily.OTHER) {
                ChunkyButton(
                    text = stringResource(R.string.battery_open_oem_settings),
                    onClick = onOpenOemSettings,
                    style = ChunkyButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BackButton(onClick = onBack)
            DuelText(
                text = stringResource(R.string.battery_title),
                style = DuelTheme.typography.title.copy(fontSize = 26.sp),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(16.dp))
        DuelText(text = stringResource(R.string.battery_intro), style = DuelTheme.typography.bodyStrong)
        Spacer(Modifier.height(12.dp))
        StatusPill(
            text = stringResource(R.string.battery_phone_family, stringResource(oem.nameRes())),
            fill = colors.lavender,
        )
        Spacer(Modifier.height(16.dp))
        if (batteryUnrestricted) {
            ChunkyCard(modifier = Modifier.fillMaxWidth(), fill = colors.green) {
                DuelText(
                    text = stringResource(R.string.battery_status_done),
                    style = DuelTheme.typography.heading.copy(fontSize = 20.sp),
                    color = colors.onBright,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
        steps.forEachIndexed { index, step ->
            StepCard(number = index + 1, text = stringResource(step.textRes()))
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun StepCard(number: Int, text: String) {
    val colors = DuelTheme.colors
    val spoken = stringResource(R.string.battery_step_number, number) + ". " + text
    ChunkyCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = spoken },
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.yellow)
                    .border(ChunkyMetrics.OutlineWidth, colors.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                DuelText(number.toString(), style = DuelTheme.typography.button, color = colors.onBright)
            }
            DuelText(text = text, style = DuelTheme.typography.bodyStrong, modifier = Modifier.weight(1f))
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

@Preview(name = "Battery guide generic small 320x640", widthDp = 320, heightDp = 640)
@Composable
private fun BatteryGuideOtherPreview() = ScreenPreview {
    BatteryGuideScreen(OemFamily.OTHER, batteryUnrestricted = false, onBack = {}, onOpenBatterySettings = {}, onOpenOemSettings = {})
}
