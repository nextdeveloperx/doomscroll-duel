package com.doomscrollduel.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.doomscrollduel.R
import com.doomscrollduel.core.designsystem.components.ChunkyCard
import com.doomscrollduel.core.designsystem.components.DuelScreen
import com.doomscrollduel.core.designsystem.components.DuelTab
import com.doomscrollduel.core.designsystem.components.DuelText
import com.doomscrollduel.core.designsystem.components.StatusPill
import com.doomscrollduel.core.designsystem.components.TabbedScreenPreview
import com.doomscrollduel.core.designsystem.components.ToggleSwitch
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import com.doomscrollduel.feature.FakeData

@Immutable
data class SettingsUiState(
    val strictLock: Boolean,
    val friendUnlock: Boolean,
    val wait10Gate: Boolean,
    val bedtimeMode: Boolean,
    val focusHours: Boolean,
    val accessibilityEnabled: Boolean,
    val batteryUnrestricted: Boolean,
)

/** Keeps the toggles while the screen is open. Permission statuses come from the system. */
@Composable
fun SettingsRoute(
    accessibilityEnabled: Boolean,
    batteryUnrestricted: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenBatteryGuide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val initial = FakeData.settings
    var strictLock by rememberSaveable { mutableStateOf(initial.strictLock) }
    var friendUnlock by rememberSaveable { mutableStateOf(initial.friendUnlock) }
    var wait10 by rememberSaveable { mutableStateOf(initial.wait10Gate) }
    var bedtime by rememberSaveable { mutableStateOf(initial.bedtimeMode) }
    var focus by rememberSaveable { mutableStateOf(initial.focusHours) }

    SettingsScreen(
        state = SettingsUiState(
            strictLock = strictLock,
            friendUnlock = friendUnlock,
            wait10Gate = wait10,
            bedtimeMode = bedtime,
            focusHours = focus,
            accessibilityEnabled = accessibilityEnabled,
            batteryUnrestricted = batteryUnrestricted,
        ),
        onStrictLockChange = { strictLock = it },
        onFriendUnlockChange = { friendUnlock = it },
        onWait10Change = { wait10 = it },
        onBedtimeChange = { bedtime = it },
        onFocusChange = { focus = it },
        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
        onOpenBatteryGuide = onOpenBatteryGuide,
        modifier = modifier,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onStrictLockChange: (Boolean) -> Unit,
    onFriendUnlockChange: (Boolean) -> Unit,
    onWait10Change: (Boolean) -> Unit,
    onBedtimeChange: (Boolean) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenBatteryGuide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DuelTheme.colors
    DuelScreen(modifier = modifier) {
        DuelText(
            text = stringResource(R.string.settings_title),
            style = DuelTheme.typography.title,
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
        )

        SectionLabel(R.string.settings_section_reels)
        SettingsCard {
            ToggleRow(R.string.settings_strict_lock, R.string.settings_strict_lock_sub, state.strictLock, onStrictLockChange)
            RowDivider()
            ToggleRow(R.string.settings_friend_unlock, R.string.settings_friend_unlock_sub, state.friendUnlock, onFriendUnlockChange)
            RowDivider()
            ToggleRow(R.string.settings_wait10, R.string.settings_wait10_sub, state.wait10Gate, onWait10Change)
        }

        SectionLabel(R.string.settings_section_time)
        SettingsCard {
            ToggleRow(R.string.settings_bedtime, R.string.settings_bedtime_sub, state.bedtimeMode, onBedtimeChange)
            RowDivider()
            ToggleRow(R.string.settings_focus, R.string.settings_focus_sub, state.focusHours, onFocusChange)
        }

        SectionLabel(R.string.settings_section_permissions)
        SettingsCard {
            StatusRow(
                titleRes = R.string.settings_accessibility,
                subtitleRes = R.string.settings_accessibility_sub,
                statusText = stringResource(if (state.accessibilityEnabled) R.string.status_on else R.string.status_off),
                statusFill = if (state.accessibilityEnabled) colors.green else colors.red,
                onClick = onOpenAccessibilitySettings,
            )
            RowDivider()
            StatusRow(
                titleRes = R.string.settings_battery,
                subtitleRes = R.string.settings_battery_sub,
                statusText = stringResource(
                    if (state.batteryUnrestricted) R.string.status_battery_ok else R.string.status_battery_restricted,
                ),
                statusFill = if (state.batteryUnrestricted) colors.green else colors.orange,
                onClick = onOpenBatteryGuide,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionLabel(textRes: Int) {
    DuelText(
        text = stringResource(textRes),
        style = DuelTheme.typography.captionStrong,
        color = DuelTheme.colors.textMuted,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    ChunkyCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    ) { content() }
}

@Composable
private fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(DuelTheme.colors.outline),
    )
}

@Composable
private fun ToggleRow(titleRes: Int, subtitleRes: Int, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val title = stringResource(titleRes)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RowText(titleRes, subtitleRes, Modifier.weight(1f))
        ToggleSwitch(checked = checked, onCheckedChange = onCheckedChange, label = title)
    }
}

/** A whole row that opens a system settings page, with its current status as a labelled pill. */
@Composable
private fun StatusRow(
    titleRes: Int,
    subtitleRes: Int,
    statusText: String,
    statusFill: Color,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(if (pressed) DuelTheme.colors.background.copy(alpha = 0.5f) else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = stringResource(R.string.settings_open_system),
                onClick = onClick,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RowText(titleRes, subtitleRes, Modifier.weight(1f))
        StatusPill(text = statusText, fill = statusFill)
    }
}

@Composable
private fun RowText(titleRes: Int, subtitleRes: Int, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        DuelText(text = stringResource(titleRes), style = DuelTheme.typography.bodyStrong)
        DuelText(
            text = stringResource(subtitleRes),
            style = DuelTheme.typography.caption,
            color = DuelTheme.colors.textMuted,
        )
    }
}

@Preview(name = "Settings 390x844", widthDp = 390, heightDp = 844)
@Composable
private fun SettingsPreview() = TabbedScreenPreview(DuelTab.SETTINGS) {
    SettingsScreen(
        state = FakeData.settings,
        onStrictLockChange = {}, onFriendUnlockChange = {}, onWait10Change = {},
        onBedtimeChange = {}, onFocusChange = {},
        onOpenAccessibilitySettings = {}, onOpenBatteryGuide = {},
    )
}

@Preview(name = "Settings permissions missing 320x640", widthDp = 320, heightDp = 640)
@Composable
private fun SettingsMissingPreview() = TabbedScreenPreview(DuelTab.SETTINGS) {
    SettingsScreen(
        state = FakeData.settings.copy(accessibilityEnabled = false, batteryUnrestricted = false),
        onStrictLockChange = {}, onFriendUnlockChange = {}, onWait10Change = {},
        onBedtimeChange = {}, onFocusChange = {},
        onOpenAccessibilitySettings = {}, onOpenBatteryGuide = {},
    )
}
