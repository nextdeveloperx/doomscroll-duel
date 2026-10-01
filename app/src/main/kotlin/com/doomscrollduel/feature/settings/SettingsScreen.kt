package com.doomscrollduel.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doomscrollduel.R
import com.doomscrollduel.core.designsystem.components.ChoiceChip
import com.doomscrollduel.core.designsystem.components.ChunkyButton
import com.doomscrollduel.core.designsystem.components.ChunkyButtonStyle
import com.doomscrollduel.core.designsystem.components.ChunkyCard
import com.doomscrollduel.core.designsystem.components.DuelScreen
import com.doomscrollduel.core.designsystem.components.DuelTab
import com.doomscrollduel.core.designsystem.components.DuelText
import com.doomscrollduel.core.designsystem.components.HardShadowText
import com.doomscrollduel.core.designsystem.components.StatusPill
import com.doomscrollduel.core.designsystem.components.StepperButton
import com.doomscrollduel.core.designsystem.components.StepperKind
import com.doomscrollduel.core.designsystem.components.TabbedScreenPreview
import com.doomscrollduel.core.designsystem.components.ToggleSwitch
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import com.doomscrollduel.domain.billing.ProFeature
import com.doomscrollduel.domain.blocking.ActiveWindow
import com.doomscrollduel.feature.paywall.UpgradePrompt
import com.doomscrollduel.feature.paywall.proFeatureReason
import com.doomscrollduel.domain.blocking.BedtimeSettings
import com.doomscrollduel.domain.blocking.BlockingSettings
import com.doomscrollduel.domain.blocking.BlockingStatus
import com.doomscrollduel.domain.blocking.Buddy
import com.doomscrollduel.domain.blocking.ClockSample
import com.doomscrollduel.domain.blocking.FocusSettings
import com.doomscrollduel.domain.blocking.SettingChange
import com.doomscrollduel.domain.blocking.TimeFormat
import com.doomscrollduel.domain.blocking.TimeRange
import com.doomscrollduel.domain.blocking.TimeRanges
import com.doomscrollduel.domain.blocking.WindowKind
import com.doomscrollduel.domain.challenge.PlayerId
import com.doomscrollduel.domain.challenge.lock.LockLength
import com.doomscrollduel.domain.challenge.lock.StrictLockConfig
import com.doomscrollduel.domain.challenge.lock.StrictLockState
import com.doomscrollduel.tracking.health.TrackingHealth
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Everything the Settings screen can do, so the screen itself stays a plain function of its state. */
class SettingsActions(
    val onChange: (SettingChange) -> Unit,
    val onOpenFocusHours: () -> Unit,
    val onFixAccessibility: () -> Unit,
    val onFixBattery: () -> Unit,
    val onFixNotifications: () -> Unit,
    val onDismissMessage: () -> Unit,
    val onSeePro: (ProFeature) -> Unit = {},
)

@Composable
fun SettingsScreen(
    ui: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    val colors = DuelTheme.colors
    val settings = ui.status.settings
    val lockRunning = ui.status.lockRunning
    val window = ui.status.window
    val bedtimeRunning = window?.kind == WindowKind.BEDTIME
    val focusRunning = window?.kind == WindowKind.FOCUS

    DuelScreen(modifier = modifier) {
        DuelText(
            text = stringResource(R.string.settings_title),
            style = DuelTheme.typography.title,
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
        )
        ui.message?.let {
            if (it is SettingsMessage.NeedsPro) {
                Spacer(Modifier.height(12.dp))
                UpgradePrompt(
                    feature = it.feature,
                    onSeePro = { actions.onDismissMessage(); actions.onSeePro(it.feature) },
                    onLater = actions.onDismissMessage,
                )
            } else {
                MessageCard(it, actions.onDismissMessage)
            }
        }

        // ----- REELS BLOCK ---------------------------------------------------------------------
        SectionLabel(R.string.settings_section_reels)
        SettingsCard {
            LimitRow(settings, enabled = !lockRunning, onChange = actions.onChange)
            RowDivider()
            ToggleRow(
                title = stringResource(R.string.settings_strict_lock) + proSuffix(ui.isPro),
                subtitle = stringResource(R.string.settings_strict_lock_sub),
                note = if (lockRunning) stringResource(R.string.settings_lock_running, TimeFormat.clock(ui.status.lockRemainingMs)) else null,
                checked = settings.strictLockEnabled,
                // While the timer runs the switch cannot be turned off.
                enabled = !(lockRunning && settings.strictLockEnabled),
                onChecked = { actions.onChange(SettingChange.StrictLockEnabled(it)) },
            )
            if (settings.strictLockEnabled) {
                RowDivider()
                LockLengthRow(settings.lockLength, enabled = !lockRunning) { actions.onChange(SettingChange.LockLengthChanged(it)) }
            }
            RowDivider()
            ToggleRow(
                title = stringResource(R.string.settings_friend_unlock),
                subtitle = stringResource(R.string.settings_friend_unlock_sub),
                note = null,
                checked = settings.friendUnlockEnabled,
                enabled = true,
                onChecked = { actions.onChange(SettingChange.FriendUnlockEnabled(it)) },
            )
            BuddyRow(settings.buddy, ui.friends) { actions.onChange(SettingChange.BuddyChanged(it)) }
            RowDivider()
            ToggleRow(
                title = stringResource(R.string.settings_wait10),
                subtitle = stringResource(R.string.settings_wait10_sub),
                note = null,
                checked = settings.wait10Enabled,
                enabled = true,
                onChecked = { actions.onChange(SettingChange.Wait10Enabled(it)) },
            )
        }

        // ----- TIME ----------------------------------------------------------------------------
        SectionLabel(R.string.settings_section_time)
        SettingsCard {
            ToggleRow(
                title = stringResource(R.string.settings_bedtime),
                subtitle = stringResource(
                    R.string.settings_bedtime_range,
                    TimeFormat.time12(settings.bedtime.range.start),
                    TimeFormat.time12(settings.bedtime.range.end),
                ),
                note = if (bedtimeRunning) windowNote(window!!, ui.status.nowMs) else null,
                checked = settings.bedtime.enabled,
                enabled = !(bedtimeRunning && settings.bedtime.enabled),
                onChecked = { actions.onChange(SettingChange.BedtimeEnabled(it)) },
            )
            if (settings.bedtime.enabled) {
                val range = settings.bedtime.range
                TimeStepper(stringResource(R.string.settings_bedtime_start), range.start, !bedtimeRunning,
                    onEarlier = { actions.onChange(SettingChange.BedtimeRangeChanged(TimeRanges.nudgeStart(range, -TimeRanges.STEP_MINUTES))) },
                    onLater = { actions.onChange(SettingChange.BedtimeRangeChanged(TimeRanges.nudgeStart(range, TimeRanges.STEP_MINUTES))) })
                TimeStepper(stringResource(R.string.settings_bedtime_end), range.end, !bedtimeRunning,
                    onEarlier = { actions.onChange(SettingChange.BedtimeRangeChanged(TimeRanges.nudgeEnd(range, -TimeRanges.STEP_MINUTES))) },
                    onLater = { actions.onChange(SettingChange.BedtimeRangeChanged(TimeRanges.nudgeEnd(range, TimeRanges.STEP_MINUTES))) })
            }
            RowDivider()
            val daysSet = settings.focus.days.count { it.value.isNotEmpty() }
            ToggleRow(
                title = stringResource(R.string.settings_focus) + proSuffix(ui.isPro),
                subtitle = if (daysSet == 0) stringResource(R.string.settings_focus_summary_none) else stringResource(R.string.settings_focus_summary, daysSet),
                note = if (focusRunning) windowNote(window!!, ui.status.nowMs) else null,
                checked = settings.focus.enabled,
                enabled = !(focusRunning && settings.focus.enabled),
                onChecked = { actions.onChange(SettingChange.FocusEnabled(it)) },
            )
            ChunkyButton(
                text = stringResource(R.string.settings_focus_edit),
                onClick = actions.onOpenFocusHours,
                style = ChunkyButtonStyle.Secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
        }

        // ----- PERMISSIONS ---------------------------------------------------------------------
        SectionLabel(R.string.settings_section_permissions)
        SettingsCard {
            PermissionRow(
                title = stringResource(R.string.settings_accessibility),
                subtitle = stringResource(R.string.settings_accessibility_sub),
                ok = ui.health.accessibilityEnabled,
                okText = stringResource(R.string.status_on),
                badText = stringResource(R.string.status_off),
                badFill = colors.red,
                onFix = actions.onFixAccessibility,
            )
            RowDivider()
            PermissionRow(
                title = stringResource(R.string.settings_battery),
                subtitle = stringResource(R.string.settings_battery_sub),
                ok = ui.health.batteryUnrestricted,
                okText = stringResource(R.string.status_battery_ok),
                badText = stringResource(R.string.status_battery_restricted),
                badFill = colors.orange,
                onFix = actions.onFixBattery,
            )
            RowDivider()
            PermissionRow(
                title = stringResource(R.string.settings_notifications),
                subtitle = stringResource(R.string.settings_notifications_sub),
                ok = ui.health.notificationsEnabled,
                okText = stringResource(R.string.status_on),
                badText = stringResource(R.string.status_off),
                badFill = colors.orange,
                onFix = actions.onFixNotifications,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ----- pieces ------------------------------------------------------------------------------------

/** " · PRO" after a row title that needs Pro, for people who are not Pro. */
@Composable
private fun proSuffix(isPro: Boolean): String = if (isPro) "" else " · " + stringResource(R.string.settings_pro_tag)

@Composable
private fun windowNote(window: ActiveWindow, nowMs: Long): String {
    val name = stringResource(if (window.kind == WindowKind.BEDTIME) R.string.window_bedtime else R.string.window_focus)
    return stringResource(R.string.settings_window_running, name, TimeFormat.clock(window.end.toEpochMilli() - nowMs))
}

@Composable
private fun MessageCard(message: SettingsMessage, onDismiss: () -> Unit) {
    val colors = DuelTheme.colors
    val text = when (message) {
        is SettingsMessage.LockRunning -> stringResource(R.string.settings_blocked_lock, TimeFormat.words(message.remainingMs))
        is SettingsMessage.WindowRunning -> stringResource(
            R.string.settings_blocked_window,
            stringResource(if (message.kind == WindowKind.BEDTIME) R.string.window_bedtime else R.string.window_focus),
            TimeFormat.words(message.remainingMs),
        )
        is SettingsMessage.NeedsPro -> proFeatureReason(message.feature)
        SettingsMessage.NeedsBuddy -> stringResource(R.string.settings_blocked_needs_buddy)
        SettingsMessage.LimitOutOfRange -> stringResource(R.string.settings_blocked_range)
    }
    Spacer(Modifier.height(12.dp))
    ChunkyCard(modifier = Modifier.fillMaxWidth(), fill = colors.red) {
        DuelText(text = text, style = DuelTheme.typography.bodyStrong, color = colors.onBright)
        Spacer(Modifier.height(8.dp))
        ChunkyButton(
            text = stringResource(R.string.settings_message_ok),
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        )
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
private fun RowText(title: String, subtitle: String, note: String?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        DuelText(text = title, style = DuelTheme.typography.bodyStrong)
        DuelText(text = subtitle, style = DuelTheme.typography.caption, color = DuelTheme.colors.textMuted)
        if (note != null) DuelText(text = note, style = DuelTheme.typography.captionStrong, color = DuelTheme.colors.orange)
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    note: String?,
    checked: Boolean,
    enabled: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RowText(title, subtitle, note, Modifier.weight(1f))
        ToggleSwitch(checked = checked, onCheckedChange = onChecked, label = title, enabled = enabled)
    }
}

@Composable
private fun LimitRow(settings: BlockingSettings, enabled: Boolean, onChange: (SettingChange) -> Unit) {
    // Steps start from the limit that is waiting for tomorrow (if any), so repeated taps keep adding up.
    val shown = settings.pendingLimit?.cap ?: settings.dailyLimit
    val spoken = stringResource(R.string.settings_limit_value_cd, shown)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RowText(
            stringResource(R.string.settings_limit_title),
            stringResource(R.string.settings_limit_sub),
            note = settings.pendingLimit?.let { stringResource(R.string.settings_limit_pending, it.cap) },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            StepperButton(
                kind = StepperKind.Decrement,
                onClick = { onChange(SettingChange.DailyLimitChanged(shown - LIMIT_STEP)) },
                contentDescription = stringResource(R.string.ds_stepper_decrease),
                enabled = enabled && shown > StrictLockConfig.MIN_CAP,
            )
            HardShadowText(
                text = settings.dailyLimit.toString(),
                fontSize = 44.sp,
                shadowDepth = 4.dp,
                modifier = Modifier
                    .widthIn(min = 120.dp)
                    .semantics { contentDescription = spoken },
            )
            StepperButton(
                kind = StepperKind.Increment,
                onClick = { onChange(SettingChange.DailyLimitChanged(shown + LIMIT_STEP)) },
                contentDescription = stringResource(R.string.ds_stepper_increase),
                enabled = enabled && shown < StrictLockConfig.MAX_CAP,
            )
        }
    }
}

private const val LIMIT_STEP = 10

@Composable
private fun LockLengthRow(length: LockLength, enabled: Boolean, onSelect: (LockLength) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DuelText(text = stringResource(R.string.settings_lock_length_title), style = DuelTheme.typography.bodyStrong)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChoiceChip(stringResource(R.string.lock_len_1h), length == LockLength.Hours(1), { onSelect(LockLength.Hours(1)) }, enabled = enabled)
            ChoiceChip(stringResource(R.string.lock_len_3h), length == LockLength.Hours(3), { onSelect(LockLength.Hours(3)) }, enabled = enabled)
            ChoiceChip(stringResource(R.string.lock_len_midnight), length == LockLength.UntilMidnight, { onSelect(LockLength.UntilMidnight) }, enabled = enabled)
        }
    }
}

@Composable
private fun BuddyRow(buddy: Buddy?, friends: List<Buddy>, onPick: (Buddy?) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DuelText(
            text = stringResource(R.string.settings_friend_unlock_quota),
            style = DuelTheme.typography.caption,
            color = DuelTheme.colors.textMuted,
        )
        DuelText(text = stringResource(R.string.settings_buddy_title), style = DuelTheme.typography.bodyStrong)
        if (friends.isEmpty()) {
            DuelText(
                text = if (buddy != null) buddy.displayName else stringResource(R.string.settings_buddy_empty),
                style = DuelTheme.typography.caption,
                color = DuelTheme.colors.textMuted,
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                friends.forEach { friend ->
                    ChoiceChip(friend.displayName, buddy?.uid == friend.uid, { onPick(friend) })
                }
            }
        }
    }
}

@Composable
internal fun TimeStepper(label: String, time: LocalTime, enabled: Boolean, onEarlier: () -> Unit, onLater: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DuelText(text = label, style = DuelTheme.typography.captionStrong, color = DuelTheme.colors.textMuted)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            StepperButton(StepperKind.Decrement, onEarlier, stringResource(R.string.settings_time_earlier, label), enabled = enabled)
            DuelText(
                text = TimeFormat.time12(time),
                style = DuelTheme.typography.button,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 120.dp),
            )
            StepperButton(StepperKind.Increment, onLater, stringResource(R.string.settings_time_later, label), enabled = enabled)
        }
    }
}

/** A permission row: live status chip, and a Fix button whenever it needs fixing. */
@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    ok: Boolean,
    okText: String,
    badText: String,
    badFill: Color,
    onFix: () -> Unit,
) {
    val colors = DuelTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RowText(title, subtitle, null, Modifier.weight(1f))
            StatusPill(text = if (ok) okText else badText, fill = if (ok) colors.green else badFill)
        }
        if (!ok) {
            ChunkyButton(
                text = stringResource(R.string.settings_fix),
                onClick = onFix,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ----- previews ----------------------------------------------------------------------------------------

private val PreviewDay = LocalDate.of(2026, 10, 1)
private val PreviewSample = ClockSample(1_790_000_000_000L, 1_000_000L, "b")

private fun previewUi(
    settings: BlockingSettings = BlockingSettings(),
    locked: Boolean = false,
    window: ActiveWindow? = null,
    health: TrackingHealth = TrackingHealth(true, true, true),
    friends: List<Buddy> = emptyList(),
    message: SettingsMessage? = null,
) = SettingsUiState(
    status = BlockingStatus(
        settings = settings,
        lock = if (locked) StrictLockState.Locked(PreviewDay, PreviewSample.wallMs, 36_000_000L, 12_000_000L, PreviewSample, 100) else StrictLockState.Idle(PreviewDay),
        pass = null,
        window = window,
        askQuotaLeft = 3,
        askPending = false,
        nowMs = PreviewSample.wallMs,
    ),
    health = health,
    friends = friends,
    message = message,
)

private val NoActions = SettingsActions({}, {}, {}, {}, {}, {})
private val Aman = Buddy(PlayerId("aman"), "Aman")

@Preview(name = "Settings default 390x844", widthDp = 390, heightDp = 844)
@Composable
private fun SettingsDefaultPreview() = TabbedScreenPreview(DuelTab.SETTINGS) {
    SettingsScreen(previewUi(), NoActions)
}

@Preview(name = "Settings lock running", widthDp = 390, heightDp = 844)
@Composable
private fun SettingsLockedPreview() = TabbedScreenPreview(DuelTab.SETTINGS) {
    SettingsScreen(
        previewUi(
            settings = BlockingSettings(strictLockEnabled = true, friendUnlockEnabled = true, buddy = Aman, wait10Enabled = true, bedtime = BedtimeSettings(true)),
            locked = true,
            friends = listOf(Aman, Buddy(PlayerId("riya"), "Riya")),
            message = SettingsMessage.LockRunning(7_200_000L),
        ),
        NoActions,
    )
}

@Preview(name = "Settings bedtime running, permissions bad", widthDp = 390, heightDp = 844)
@Composable
private fun SettingsBadPermissionsPreview() = TabbedScreenPreview(DuelTab.SETTINGS) {
    SettingsScreen(
        previewUi(
            settings = BlockingSettings(bedtime = BedtimeSettings(true), focus = FocusSettings(true, mapOf(DayOfWeek.MONDAY to listOf(TimeRange(LocalTime.of(16, 0), LocalTime.of(18, 0)))))),
            window = ActiveWindow(WindowKind.BEDTIME, Instant.ofEpochMilli(PreviewSample.wallMs - 3_600_000L), Instant.ofEpochMilli(PreviewSample.wallMs + 21_600_000L)),
            health = TrackingHealth(accessibilityEnabled = false, batteryUnrestricted = false, notificationsEnabled = false),
        ),
        NoActions,
    )
}

@Preview(name = "Settings small 320x640", widthDp = 320, heightDp = 640)
@Composable
private fun SettingsSmallPreview() = TabbedScreenPreview(DuelTab.SETTINGS) {
    SettingsScreen(previewUi(settings = BlockingSettings(strictLockEnabled = true, bedtime = BedtimeSettings(true))), NoActions)
}
