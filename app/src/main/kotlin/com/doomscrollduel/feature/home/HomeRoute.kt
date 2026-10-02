package com.doomscrollduel.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doomscrollduel.core.common.openAccessibilitySettings
import com.doomscrollduel.domain.analytics.UsageDataChoice
import com.doomscrollduel.feature.legal.AccessibilityEntryViewModel
import com.doomscrollduel.feature.legal.UsageDataCard
import com.doomscrollduel.feature.legal.UsageDataPromptViewModel
import com.doomscrollduel.tracking.health.TrackingIssue

/** Home with live data: counts from Room, status from the system. */
@Composable
fun HomeRoute(
    onNewBattle: () -> Unit,
    onOpenBattle: (String) -> Unit,
    onOpenBatteryGuide: () -> Unit,
    onOpenDisclosure: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenFriends: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    entry: AccessibilityEntryViewModel = hiltViewModel(),
    usagePrompt: UsageDataPromptViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val session: com.doomscrollduel.feature.auth.SessionViewModel = hiltViewModel()
    val profileState by session.profile.collectAsStateWithLifecycle()
    // The avatar shows the real person: their name once known, a question mark for a guest.
    val avatarName = when (val p = profileState) {
        is com.doomscrollduel.domain.social.ProfileState.Ready -> p.profile.displayName
        com.doomscrollduel.domain.social.ProfileState.SignedOut -> "?"
        else -> ""
    }
    val usageChoice by usagePrompt.choice.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The user may have just come back from system settings.
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshHealth()
        onPauseOrDispose { }
    }

    HomeScreen(
        state = state,
        onNewBattle = onNewBattle,
        onOpenBattle = { state.battle?.duelId?.takeIf { it.isNotEmpty() }?.let(onOpenBattle) },
        onOpenSettings = onOpenSettings,
        onOpenProfile = onOpenProfile,
        onOpenFriends = onOpenFriends,
        avatarName = avatarName,
        onFixTracking = { issue ->
            when (issue) {
                // The system screen may only be opened after the person agreed to the disclosure.
                TrackingIssue.CONSENT_NEEDED -> onOpenDisclosure()
                TrackingIssue.ACCESSIBILITY_OFF -> if (entry.mayOpenSettings()) context.openAccessibilitySettings() else onOpenDisclosure()
                TrackingIssue.BATTERY_RESTRICTED -> onOpenBatteryGuide()
                TrackingIssue.NONE -> Unit
            }
        },
        modifier = modifier,
        extraTop = if (usageChoice == UsageDataChoice.UNDECIDED) {
            { UsageDataCard(onAllow = { usagePrompt.answer(true) }, onDecline = { usagePrompt.answer(false) }) }
        } else {
            null
        },
    )
}
