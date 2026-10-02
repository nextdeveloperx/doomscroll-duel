package com.doomscrollduel.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doomscrollduel.R
import com.doomscrollduel.core.designsystem.components.DuelIcon
import com.doomscrollduel.core.designsystem.components.DuelText
import com.doomscrollduel.core.designsystem.components.StepperButton
import com.doomscrollduel.core.designsystem.components.StepperKind
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import com.doomscrollduel.domain.analytics.UsageDataChoice
import com.doomscrollduel.domain.billing.ProFeature
import com.doomscrollduel.domain.blocking.ActiveWindow
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
import com.doomscrollduel.feature.paywall.proFeatureReason
import com.doomscrollduel.feature.settings.neon.Neon
import com.doomscrollduel.feature.settings.neon.NeonButton
import com.doomscrollduel.feature.settings.neon.NeonCard
import com.doomscrollduel.feature.settings.neon.NeonIcons
import com.doomscrollduel.feature.settings.neon.IconBadge
import com.doomscrollduel.feature.settings.neon.LimitDial
import com.doomscrollduel.feature.settings.neon.NText
import com.doomscrollduel.feature.settings.neon.ProChip
import com.doomscrollduel.feature.settings.neon.RoundStep
import com.doomscrollduel.feature.settings.neon.SectionHeader
import com.doomscrollduel.feature.settings.neon.SegmentedSwitch
import com.doomscrollduel.feature.settings.neon.TagPill
import com.doomscrollduel.feature.settings.neon.neonBackground
import com.doomscrollduel.tracking.health.TrackingHealth
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.launch

/** Everything the Settings screen can do, so the screen itself stays a plain function of its state. */
class SettingsActions(
    val onChange: (SettingChange) -> Unit,
    val onOpenFocusHours: () -> Unit,
    val onFixAccessibility: () -> Unit,
    val onFixBattery: () -> Unit,
    val onFixNotifications: () -> Unit,
    val onDismissMessage: () -> Unit,
    val onSeePro: (ProFeature) -> Unit = {},
    val onOpenPrivacy: () -> Unit = {},
    val onOpenTerms: () -> Unit = {},
    val onOpenDisclosure: () -> Unit = {},
    val onOpenDeleteAccount: () -> Unit = {},
    val onUsageData: (Boolean) -> Unit = {},
    val onCounterBadge: (Boolean) -> Unit = {},
    val onOpenFriends: () -> Unit = {},
    val onSignIn: () -> Unit = {},
    val onSignOut: () -> Unit = {},
    val onSetUsername: () -> Unit = {},
)

/**
 * Settings in the dark glass-and-violet design: a header, a "Protection Active" summary, the reels-block controls with the
 * limit ring, the time controls, then permissions and account. Changes apply the moment they are made (as they always did),
 * so "Save & Apply Settings" at the bottom confirms and goes back.
 */
@Composable
fun SettingsScreen(
    ui: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    counterBadge: Boolean = true,
    /** The signed-in person's username, or null when signed out. */
    accountUsername: String? = null,
    signedIn: Boolean = false,
    /** Signed in but no username chosen yet: shows the "Username set karo" row. */
    needsUsername: Boolean = false,
    /** Settings opens from the gear on Home, so it has a back button. Null hides it. */
    onBack: (() -> Unit)? = null,
) {
    val settings = ui.status.settings
    val lockRunning = ui.status.lockRunning
    val window = ui.status.window
    val bedtimeRunning = window?.kind == WindowKind.BEDTIME
    val focusRunning = window?.kind == WindowKind.FOCUS
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var permissionsY by remember { mutableIntStateOf(0) }
    var buddyOpen by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize().neonBackground()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scroll)
                .padding(horizontal = 14.dp),
        ) {
            Header(
                onBack = onBack,
                onGear = { scope.launch { scroll.animateScrollTo(permissionsY) } },
            )
            ui.message?.let { MessageBlock(it, actions) }

            ProtectionCard(limit = settings.dailyLimit, lockRunning = lockRunning)

            // ----- REELS BLOCK -----
            SectionHeader(
                icon = NeonIcons.Reels,
                title = stringResource(R.string.settings_section_reels),
                subtitle = stringResource(R.string.sn_reels_sub),
                pill = stringResource(R.string.sn_reels_pill),
            )
            LimitCard(settings, enabled = !lockRunning, onChange = actions.onChange)
            Gap()
            ToggleCard(
                icon = NeonIcons.Bars,
                title = stringResource(R.string.settings_counter_badge),
                subtitle = stringResource(R.string.settings_counter_badge_sub),
                checked = counterBadge,
                onChecked = actions.onCounterBadge,
            )
            Gap()
            ToggleCard(
                icon = NeonIcons.Lock,
                title = stringResource(R.string.settings_strict_lock),
                titleChip = if (ui.isPro) null else stringResource(R.string.settings_pro_tag),
                subtitle = stringResource(R.string.settings_strict_lock_sub),
                note = if (lockRunning) stringResource(R.string.settings_lock_running, TimeFormat.clock(ui.status.lockRemainingMs)) else null,
                checked = settings.strictLockEnabled,
                // While the timer runs the switch cannot be turned off.
                enabled = !(lockRunning && settings.strictLockEnabled),
                onChecked = { actions.onChange(SettingChange.StrictLockEnabled(it)) },
            ) {
                if (settings.strictLockEnabled) {
                    LockLengthChoices(settings.lockLength, enabled = !lockRunning) { actions.onChange(SettingChange.LockLengthChanged(it)) }
                }
            }
            Gap()
            ToggleCard(
                icon = NeonIcons.People,
                title = stringResource(R.string.settings_friend_unlock),
                subtitle = stringResource(R.string.settings_friend_unlock_sub),
                checked = settings.friendUnlockEnabled,
                onChecked = { actions.onChange(SettingChange.FriendUnlockEnabled(it)) },
                glow = true,
            ) {
                NText(
                    stringResource(R.string.settings_friend_unlock_quota),
                    12.5.sp,
                    color = Neon.Muted,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                BuddyRow(
                    buddy = settings.buddy,
                    friends = ui.friends,
                    open = buddyOpen,
                    onToggle = { if (ui.friends.isNotEmpty()) buddyOpen = !buddyOpen },
                    onPick = {
                        actions.onChange(SettingChange.BuddyChanged(it))
                        buddyOpen = false
                    },
                )
            }
            Gap()
            ToggleCard(
                icon = NeonIcons.Hourglass,
                title = stringResource(R.string.settings_wait10),
                subtitle = stringResource(R.string.settings_wait10_sub),
                checked = settings.wait10Enabled,
                onChecked = { actions.onChange(SettingChange.Wait10Enabled(it)) },
                glow = true,
            )

            // ----- TIME -----
            SectionHeader(
                icon = NeonIcons.Clock,
                title = stringResource(R.string.settings_section_time),
                subtitle = stringResource(R.string.sn_time_sub),
                pill = stringResource(R.string.sn_time_pill),
            )
            WaitFlowCard(active = settings.wait10Enabled)
            Gap()
            ToggleCard(
                icon = NeonIcons.Moon,
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
            ) {
                if (settings.bedtime.enabled) {
                    val range = settings.bedtime.range
                    NeonTimeStepper(
                        stringResource(R.string.settings_bedtime_start), range.start, !bedtimeRunning,
                        onEarlier = { actions.onChange(SettingChange.BedtimeRangeChanged(TimeRanges.nudgeStart(range, -TimeRanges.STEP_MINUTES))) },
                        onLater = { actions.onChange(SettingChange.BedtimeRangeChanged(TimeRanges.nudgeStart(range, TimeRanges.STEP_MINUTES))) },
                    )
                    NeonTimeStepper(
                        stringResource(R.string.settings_bedtime_end), range.end, !bedtimeRunning,
                        onEarlier = { actions.onChange(SettingChange.BedtimeRangeChanged(TimeRanges.nudgeEnd(range, -TimeRanges.STEP_MINUTES))) },
                        onLater = { actions.onChange(SettingChange.BedtimeRangeChanged(TimeRanges.nudgeEnd(range, TimeRanges.STEP_MINUTES))) },
                    )
                }
            }
            Gap()
            val daysSet = settings.focus.days.count { it.value.isNotEmpty() }
            ToggleCard(
                icon = NeonIcons.Clock,
                title = stringResource(R.string.settings_focus),
                titleChip = if (ui.isPro) null else stringResource(R.string.settings_pro_tag),
                subtitle = if (daysSet == 0) stringResource(R.string.settings_focus_summary_none) else stringResource(R.string.settings_focus_summary, daysSet),
                note = if (focusRunning) windowNote(window!!, ui.status.nowMs) else null,
                checked = settings.focus.enabled,
                enabled = !(focusRunning && settings.focus.enabled),
                onChecked = { actions.onChange(SettingChange.FocusEnabled(it)) },
            ) {
                NeonButton(
                    text = stringResource(R.string.settings_focus_edit),
                    onClick = actions.onOpenFocusHours,
                    filled = false,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }

            // ----- PERMISSIONS -----
            val allGood = ui.health.accessibilityEnabled && ui.health.batteryUnrestricted && ui.health.notificationsEnabled
            Box(Modifier.onGloballyPositioned { permissionsY = it.positionInParent().y.toInt() }) {
                SectionHeader(
                    icon = NeonIcons.Shield,
                    title = stringResource(R.string.settings_section_permissions),
                    subtitle = stringResource(R.string.sn_permissions_sub),
                    pill = stringResource(if (allGood) R.string.sn_permissions_ok else R.string.sn_permissions_todo),
                    pillDot = if (allGood) Neon.Green else Neon.Orange,
                )
            }
            PermissionCard(
                icon = NeonIcons.Person,
                title = stringResource(R.string.settings_accessibility),
                subtitle = stringResource(R.string.settings_accessibility_sub),
                ok = ui.health.accessibilityEnabled,
                okText = stringResource(R.string.status_on),
                badText = stringResource(R.string.status_off),
                badColor = Neon.Red,
                onFix = actions.onFixAccessibility,
            )
            Gap()
            PermissionCard(
                icon = NeonIcons.Flame,
                title = stringResource(R.string.settings_battery),
                subtitle = stringResource(R.string.settings_battery_sub),
                ok = ui.health.batteryUnrestricted,
                okText = stringResource(R.string.status_battery_ok),
                badText = stringResource(R.string.status_battery_restricted),
                badColor = Neon.Orange,
                onFix = actions.onFixBattery,
            )
            Gap()
            PermissionCard(
                icon = NeonIcons.Bell,
                title = stringResource(R.string.settings_notifications),
                subtitle = stringResource(R.string.settings_notifications_sub),
                ok = ui.health.notificationsEnabled,
                okText = stringResource(R.string.status_on),
                badText = stringResource(R.string.status_off),
                badColor = Neon.Orange,
                onFix = actions.onFixNotifications,
            )

            // ----- ACCOUNT AND PRIVACY -----
            SectionHeader(
                icon = NeonIcons.Person,
                title = stringResource(R.string.settings_section_account),
                subtitle = stringResource(R.string.sn_account_sub),
                pill = stringResource(R.string.sn_account_pill),
            )
            NeonCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                if (signedIn) {
                    if (accountUsername != null) {
                        NText(
                            stringResource(R.string.settings_signed_in_as, accountUsername),
                            14.sp,
                            weight = FontWeight.ExtraBold,
                            color = Neon.VioletLight,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                    if (needsUsername) {
                        LinkRow(R.string.settings_set_username, R.string.settings_set_username_sub, actions.onSetUsername)
                        Line()
                    }
                    LinkRow(R.string.friends_account_row, R.string.friends_account_row_sub, actions.onOpenFriends)
                    Line()
                    LinkRow(R.string.settings_sign_out, R.string.settings_sign_out_sub, actions.onSignOut)
                } else {
                    LinkRow(R.string.settings_sign_in, R.string.settings_sign_in_sub, actions.onSignIn)
                }
                Line()
                LinkRow(R.string.settings_privacy_policy, R.string.settings_privacy_policy_sub, actions.onOpenPrivacy)
                Line()
                LinkRow(R.string.settings_terms, R.string.settings_terms_sub, actions.onOpenTerms)
                Line()
                LinkRow(R.string.settings_disclosure_again, R.string.settings_disclosure_again_sub, actions.onOpenDisclosure)
                Line()
                LinkRow(R.string.settings_delete_account, R.string.settings_delete_account_sub, actions.onOpenDeleteAccount, danger = true)
            }
            Gap()
            ToggleCard(
                icon = NeonIcons.Bars,
                title = stringResource(R.string.settings_usage_data),
                subtitle = stringResource(R.string.settings_usage_data_sub),
                checked = ui.usageData == UsageDataChoice.ALLOWED,
                onChecked = actions.onUsageData,
            )
            Spacer(Modifier.height(120.dp)) // room for the bottom button
        }

        SaveBar(onSave = { onBack?.invoke() })
    }
}

// ----- header and summary ------------------------------------------------------------------------------------------

@Composable
private fun Header(onBack: (() -> Unit)?, onGear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) {
            val back = stringResource(R.string.sn_back)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0B0716))
                    .border(1.6.dp, Brush.linearGradient(listOf(Neon.VioletLight, Neon.Violet.copy(alpha = 0.3f))), CircleShape)
                    .clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = back },
                contentAlignment = Alignment.Center,
            ) {
                DuelIcon(NeonIcons.Back, tint = Color.White, contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            NText(stringResource(R.string.settings_title), 28.sp, weight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.semantics { heading() })
            NText(stringResource(R.string.sn_subtitle), 13.sp, color = Neon.Muted, maxLines = 1)
        }
        val gear = stringResource(R.string.sn_jump_permissions)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF110B20))
                .border(1.dp, Neon.Violet.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClick = onGear)
                .semantics { contentDescription = gear },
            contentAlignment = Alignment.Center,
        ) {
            DuelIcon(NeonIcons.Gear, tint = Color.White, contentDescription = null, modifier = Modifier.size(23.dp))
        }
    }
}

@Composable
private fun ProtectionCard(limit: Int, lockRunning: Boolean) {
    val unit = stringResource(R.string.sn_reels_per_day)
    NeonCard(glow = true, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .drawBehind { drawCircle(Brush.radialGradient(listOf(Neon.Violet.copy(alpha = 0.45f), Color.Transparent)), radius = size.minDimension * 0.75f) },
                contentAlignment = Alignment.Center,
            ) {
                DuelIcon(NeonIcons.ShieldCheck, tint = Neon.VioletLight, contentDescription = null, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                NText(
                    stringResource(if (lockRunning) R.string.sn_protection_lock_title else R.string.sn_protection_title),
                    18.sp,
                    weight = FontWeight.ExtraBold,
                    color = Color(0xFFC7A6FF),
                    maxLines = 1,
                )
                NText(stringResource(R.string.sn_protection_line1), 12.sp, color = Neon.Muted, maxLines = 1)
                NText(stringResource(R.string.sn_protection_line2), 12.sp, color = Neon.Muted, maxLines = 1)
            }
            Box(Modifier.width(1.dp).height(52.dp).background(Neon.Violet.copy(alpha = 0.3f)))
            Column(
                modifier = Modifier.padding(start = 12.dp).semantics(mergeDescendants = true) { contentDescription = "$limit $unit" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    DuelIcon(NeonIcons.Bars, tint = Neon.Violet, contentDescription = null, modifier = Modifier.size(16.dp))
                    NText(limit.toString(), 28.sp, weight = FontWeight.ExtraBold, maxLines = 1)
                }
                NText(unit, 12.sp, color = Neon.Muted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun LimitCard(settings: BlockingSettings, enabled: Boolean, onChange: (SettingChange) -> Unit) {
    // Steps start from the limit that is waiting for tomorrow (if any), so repeated taps keep adding up.
    val shown = settings.pendingLimit?.cap ?: settings.dailyLimit
    val spoken = stringResource(R.string.sn_dial_cd, shown)
    NeonCard(glow = true, contentPadding = PaddingValues(start = 6.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(172.dp).height(124.dp).semantics(mergeDescendants = true) { contentDescription = spoken }) {
                Box(Modifier.align(Alignment.Center)) {
                    LimitDial(limit = settings.dailyLimit, unit = stringResource(R.string.sn_reels_per_day), min = StrictLockConfig.MIN_CAP, max = StrictLockConfig.MAX_CAP)
                }
                Box(Modifier.align(Alignment.CenterStart)) {
                    RoundStep(
                        plus = false,
                        onClick = { onChange(SettingChange.DailyLimitChanged(shown - LIMIT_STEP)) },
                        label = stringResource(R.string.ds_stepper_decrease),
                        enabled = enabled && shown > StrictLockConfig.MIN_CAP,
                    )
                }
                Box(Modifier.align(Alignment.CenterEnd)) {
                    RoundStep(
                        plus = true,
                        onClick = { onChange(SettingChange.DailyLimitChanged(shown + LIMIT_STEP)) },
                        label = stringResource(R.string.ds_stepper_increase),
                        enabled = enabled && shown < StrictLockConfig.MAX_CAP,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                NText(stringResource(R.string.settings_limit_title), 16.sp, weight = FontWeight.ExtraBold, maxLines = 1)
                NText(stringResource(R.string.settings_limit_sub), 12.5.sp, color = Neon.Muted, lineHeight = 17.sp)
                settings.pendingLimit?.let {
                    NText(stringResource(R.string.settings_limit_pending, it.cap), 12.5.sp, color = Neon.Orange, weight = FontWeight.ExtraBold, lineHeight = 17.sp)
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF110B20))
                        .border(1.dp, Neon.Violet.copy(alpha = 0.3f), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    DuelIcon(NeonIcons.Flame, tint = Neon.Pink, contentDescription = null, modifier = Modifier.size(14.dp))
                    NText(stringResource(R.string.sn_limit_pill), 11.5.sp, maxLines = 1)
                }
            }
        }
    }
}

private const val LIMIT_STEP = 10

// ----- rows --------------------------------------------------------------------------------------------------------

@Composable
private fun Gap() = Spacer(Modifier.height(10.dp))

@Composable
private fun Line() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Neon.Violet.copy(alpha = 0.18f)))
}

/**
 * A card with an icon, a title (and a gold PRO chip), one line of help, an Off/On switch and, below, anything that belongs
 * to the setting (lock length, quota text, time pickers).
 */
@Composable
private fun ToggleCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    note: String? = null,
    titleChip: String? = null,
    enabled: Boolean = true,
    glow: Boolean = false,
    below: @Composable () -> Unit = {},
) {
    NeonCard(glow = glow, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBadge(icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NText(title, 15.5.sp, weight = FontWeight.ExtraBold, lineHeight = 19.sp, modifier = Modifier.weight(1f, fill = false))
                    titleChip?.let { ProChip(it) }
                }
                NText(subtitle, 12.5.sp, color = Neon.Muted, lineHeight = 17.sp)
                note?.let { NText(it, 12.5.sp, color = Neon.Orange, weight = FontWeight.ExtraBold, lineHeight = 17.sp) }
            }
            SegmentedSwitch(
                checked = checked,
                onChecked = onChecked,
                label = title,
                offText = stringResource(R.string.sn_off),
                onText = stringResource(R.string.sn_on),
                enabled = enabled,
            )
        }
        below()
    }
}

/** A small "(label) - time +" picker in the violet style. */
@Composable
private fun NeonTimeStepper(label: String, time: LocalTime, enabled: Boolean, onEarlier: () -> Unit, onLater: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        NText(label, 13.sp, color = Neon.Muted, weight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        RoundStep(plus = false, onClick = onEarlier, label = stringResource(R.string.settings_time_earlier, label), enabled = enabled)
        NText(TimeFormat.time12(time), 16.sp, weight = FontWeight.ExtraBold, align = TextAlign.Center, modifier = Modifier.width(92.dp))
        RoundStep(plus = true, onClick = onLater, label = stringResource(R.string.settings_time_later, label), enabled = enabled)
    }
}

@Composable
private fun LockLengthChoices(length: LockLength, enabled: Boolean, onSelect: (LockLength) -> Unit) {
    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        NText(stringResource(R.string.settings_lock_length_title), 13.sp, color = Neon.Muted, weight = FontWeight.ExtraBold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice(stringResource(R.string.lock_len_1h), length == LockLength.Hours(1), enabled) { onSelect(LockLength.Hours(1)) }
            Choice(stringResource(R.string.lock_len_3h), length == LockLength.Hours(3), enabled) { onSelect(LockLength.Hours(3)) }
            Choice(stringResource(R.string.lock_len_midnight), length == LockLength.UntilMidnight, enabled) { onSelect(LockLength.UntilMidnight) }
        }
    }
}

@Composable
private fun Choice(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .then(
                if (selected) Modifier.background(Brush.verticalGradient(listOf(Color(0xFFA77BFF), Color(0xFF7C4DFF))))
                else Modifier.background(Color(0xFF110B20)).border(1.dp, Neon.Violet.copy(alpha = 0.35f), shape),
            )
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        NText(text, 13.5.sp, weight = FontWeight.ExtraBold, color = if (enabled) Color.White else Neon.Muted, maxLines = 1)
    }
}

/** "Unlock wala dost": a row you tap to open the list of friends who can approve an unlock. */
@Composable
private fun BuddyRow(buddy: Buddy?, friends: List<Buddy>, open: Boolean, onToggle: () -> Unit, onPick: (Buddy?) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.padding(top = 10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Brush.horizontalGradient(listOf(Color(0xFF120C22), Color(0xFF1A1030))))
                .border(1.dp, Neon.Violet.copy(alpha = 0.3f), shape)
                .clickable(enabled = friends.isNotEmpty(), role = Role.Button, onClick = onToggle)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconBadge(NeonIcons.People, size = 38.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                NText(stringResource(R.string.settings_buddy_title), 15.sp, weight = FontWeight.ExtraBold, maxLines = 1)
                NText(
                    text = when {
                        buddy != null -> buddy.displayName
                        friends.isEmpty() -> stringResource(R.string.settings_buddy_empty)
                        else -> stringResource(R.string.settings_buddy_none)
                    },
                    size = 12.sp,
                    color = Neon.Muted,
                    lineHeight = 16.sp,
                )
            }
            if (friends.isNotEmpty()) DuelIcon(NeonIcons.ChevronRight, tint = Neon.Muted, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        if (open) {
            Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                friends.forEach { friend -> Choice(friend.displayName, buddy?.uid == friend.uid) { onPick(friend) } }
            }
        }
    }
}

/** The "Wait 10s" picture: open reels, wait ten seconds, then you are in. Greyed out while the gate is off. */
@Composable
private fun WaitFlowCard(active: Boolean) {
    NeonCard(glow = true, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBadge(NeonIcons.Hourglass)
            Column {
                NText("10s", 24.sp, weight = FontWeight.ExtraBold, maxLines = 1)
                NText(stringResource(R.string.sn_wait_time), 12.sp, color = Neon.Muted, maxLines = 1)
            }
            Column(Modifier.weight(1f)) {
                Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                    val y = size.height / 2f
                    val xs = listOf(size.width / 6f, size.width / 2f, size.width * 5f / 6f)
                    val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 7f))
                    drawLine(Neon.Violet.copy(alpha = 0.5f), Offset(xs[0] + 14.dp.toPx(), y), Offset(xs[1] - 14.dp.toPx(), y), strokeWidth = 1.5.dp.toPx(), pathEffect = dash)
                    drawLine(Neon.Violet.copy(alpha = 0.5f), Offset(xs[1] + 14.dp.toPx(), y), Offset(xs[2] - 14.dp.toPx(), y), strokeWidth = 1.5.dp.toPx(), pathEffect = dash)
                    xs.forEachIndexed { i, x ->
                        val lit = i == 1 && active
                        if (lit) drawCircle(Neon.Violet.copy(alpha = 0.35f), radius = 13.dp.toPx(), center = Offset(x, y))
                        drawCircle(if (lit) Neon.Violet else Color(0xFF0B0716), radius = 8.dp.toPx(), center = Offset(x, y))
                        drawCircle(if (lit) Neon.VioletLight else Neon.Muted.copy(alpha = 0.6f), radius = 8.dp.toPx(), center = Offset(x, y), style = Stroke(1.5.dp.toPx()))
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    listOf(R.string.sn_flow_open, R.string.sn_flow_wait, R.string.sn_flow_then).forEachIndexed { i, res ->
                        NText(
                            stringResource(res), 10.5.sp,
                            color = if (i == 1 && active) Neon.Text else Neon.Muted,
                            weight = if (i == 1 && active) FontWeight.ExtraBold else FontWeight.SemiBold,
                            align = TextAlign.Center, maxLines = 2, lineHeight = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            IconBadge(NeonIcons.Reels, rounded = true)
        }
    }
}

@Composable
private fun PermissionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    ok: Boolean,
    okText: String,
    badText: String,
    badColor: Color,
    onFix: () -> Unit,
) {
    NeonCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBadge(icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NText(title, 15.5.sp, weight = FontWeight.ExtraBold, lineHeight = 19.sp)
                NText(subtitle, 12.5.sp, color = Neon.Muted, lineHeight = 17.sp)
            }
            TagPill(if (ok) okText else badText, dot = if (ok) Neon.Green else badColor)
        }
        if (!ok) {
            NeonButton(
                text = stringResource(R.string.settings_fix),
                onClick = onFix,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
    }
}

/** A tappable row that opens another page, with a chevron. [danger] colours the title red for delete account. */
@Composable
private fun LinkRow(titleRes: Int, subRes: Int, onClick: () -> Unit, danger: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            NText(stringResource(titleRes), 15.sp, weight = FontWeight.ExtraBold, color = if (danger) Neon.Red else Neon.Text, lineHeight = 19.sp)
            NText(stringResource(subRes), 12.5.sp, color = Neon.Muted, lineHeight = 17.sp)
        }
        DuelIcon(NeonIcons.ChevronRight, tint = if (danger) Neon.Red else Neon.Muted, contentDescription = null, modifier = Modifier.size(20.dp))
    }
}


@Composable
private fun windowNote(window: ActiveWindow, nowMs: Long): String {
    val name = stringResource(if (window.kind == WindowKind.BEDTIME) R.string.window_bedtime else R.string.window_focus)
    return stringResource(R.string.settings_window_running, name, TimeFormat.clock(window.end.toEpochMilli() - nowMs))
}

// ----- messages and the bottom button ------------------------------------------------------------------------------

@Composable
private fun MessageBlock(message: SettingsMessage, actions: SettingsActions) {
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
    val pro = message as? SettingsMessage.NeedsPro
    Box(Modifier.padding(bottom = 10.dp)) {
        NeonCard(contentPadding = PaddingValues(14.dp), modifier = Modifier.border(1.dp, (if (pro != null) Neon.Gold else Neon.Red).copy(alpha = 0.6f), RoundedCornerShape(18.dp))) {
            NText(text, 14.sp, weight = FontWeight.ExtraBold, lineHeight = 19.sp)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (pro != null) {
                    NeonButton(stringResource(R.string.upgrade_prompt_see), { actions.onDismissMessage(); actions.onSeePro(pro.feature) }, Modifier.weight(1f))
                    NeonButton(stringResource(R.string.upgrade_prompt_later), actions.onDismissMessage, Modifier.weight(1f), filled = false)
                } else {
                    NeonButton(stringResource(R.string.settings_message_ok), actions.onDismissMessage, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** "Save & Apply Settings". Everything on this page is saved as it is changed, so this confirms and goes back. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.SaveBar(onSave: () -> Unit) {
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Neon.Bg0.copy(alpha = 0.94f), Neon.Bg0)))
            .navigationBarsPadding()
            .padding(start = 36.dp, end = 36.dp, top = 28.dp, bottom = 10.dp),
    ) {
        val shape = RoundedCornerShape(50)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .drawBehind { drawRoundRect(Neon.Violet.copy(alpha = 0.28f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height), topLeft = Offset(-4f, -4f), size = androidx.compose.ui.geometry.Size(size.width + 8f, size.height + 8f)) }
                .clip(shape)
                .background(Neon.Gradient)
                .border(1.2.dp, Color.White.copy(alpha = 0.3f), shape)
                .clickable(role = Role.Button, onClick = onSave)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                DuelIcon(NeonIcons.Check, tint = Color(0xFF6C4BFF), contentDescription = null, modifier = Modifier.size(24.dp))
            }
            NText(stringResource(R.string.sn_save), 17.sp, weight = FontWeight.ExtraBold, maxLines = 1, align = TextAlign.Center, modifier = Modifier.weight(1f))
            DuelIcon(NeonIcons.ArrowRight, tint = Color.White, contentDescription = null, modifier = Modifier.size(24.dp).padding(end = 2.dp))
            Spacer(Modifier.width(8.dp))
        }
    }
}

// ----- shared with the Focus hours screen ---------------------------------------------------------------------------

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
                modifier = Modifier.width(120.dp),
            )
            StepperButton(StepperKind.Increment, onLater, stringResource(R.string.settings_time_later, label), enabled = enabled)
        }
    }
}

// ----- previews ----------------------------------------------------------------------------------------------------

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
private fun SettingsDefaultPreview() = DuelTheme { SettingsScreen(previewUi(), NoActions, onBack = {}) }

@Preview(name = "Settings lock running", widthDp = 390, heightDp = 844)
@Composable
private fun SettingsLockedPreview() = DuelTheme {
    SettingsScreen(
        previewUi(
            settings = BlockingSettings(strictLockEnabled = true, friendUnlockEnabled = true, buddy = Aman, wait10Enabled = true, bedtime = BedtimeSettings(true)),
            locked = true,
            friends = listOf(Aman, Buddy(PlayerId("riya"), "Riya")),
            message = SettingsMessage.LockRunning(7_200_000L),
        ),
        NoActions,
        onBack = {},
    )
}

@Preview(name = "Settings bedtime running, permissions bad", widthDp = 390, heightDp = 844)
@Composable
private fun SettingsBadPermissionsPreview() = DuelTheme {
    SettingsScreen(
        previewUi(
            settings = BlockingSettings(bedtime = BedtimeSettings(true), focus = FocusSettings(true, mapOf(DayOfWeek.MONDAY to listOf(TimeRange(LocalTime.of(16, 0), LocalTime.of(18, 0)))))),
            window = ActiveWindow(WindowKind.BEDTIME, Instant.ofEpochMilli(PreviewSample.wallMs - 3_600_000L), Instant.ofEpochMilli(PreviewSample.wallMs + 21_600_000L)),
            health = TrackingHealth(accessibilityEnabled = false, batteryUnrestricted = false, notificationsEnabled = false),
        ),
        NoActions,
        onBack = {},
    )
}
