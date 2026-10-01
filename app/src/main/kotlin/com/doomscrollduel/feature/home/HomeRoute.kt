package com.doomscrollduel.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doomscrollduel.core.common.openAccessibilitySettings
import com.doomscrollduel.tracking.health.TrackingIssue

/** Home with live data: counts from Room, status from the system. */
@Composable
fun HomeRoute(
    onNewBattle: () -> Unit,
    onOpenBattle: () -> Unit,
    onOpenBatteryGuide: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The user may have just come back from system settings.
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshHealth()
        onPauseOrDispose { }
    }

    HomeScreen(
        state = state,
        onNewBattle = onNewBattle,
        onOpenBattle = onOpenBattle,
        onFixTracking = { issue ->
            when (issue) {
                TrackingIssue.ACCESSIBILITY_OFF -> context.openAccessibilitySettings()
                TrackingIssue.BATTERY_RESTRICTED -> onOpenBatteryGuide()
                TrackingIssue.NONE -> Unit
            }
        },
        modifier = modifier,
    )
}
