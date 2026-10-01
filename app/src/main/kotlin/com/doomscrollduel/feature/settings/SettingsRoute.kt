package com.doomscrollduel.feature.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doomscrollduel.core.common.openAccessibilitySettings
import com.doomscrollduel.core.common.openAppNotificationSettings
import com.doomscrollduel.domain.billing.ProFeature

/** Settings wired to the real blocking engine and the live permission status. */
@Composable
fun SettingsRoute(
    onOpenBatteryGuide: () -> Unit,
    onOpenFocusHours: () -> Unit,
    onOpenPaywall: (ProFeature) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The user may have just come back from a system settings page.
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshHealth()
        onPauseOrDispose { }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshHealth()
    }
    val actions = remember(viewModel, context) {
        SettingsActions(
            onChange = viewModel::change,
            onOpenFocusHours = { if (viewModel.canOpenFocusEditor()) onOpenFocusHours() },
            onFixAccessibility = { context.openAccessibilitySettings() },
            onFixBattery = onOpenBatteryGuide,
            onFixNotifications = { fixNotifications(context) { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) } },
            onDismissMessage = viewModel::dismissMessage,
            onSeePro = onOpenPaywall,
        )
    }
    SettingsScreen(ui = ui, actions = actions, modifier = modifier)
}

/**
 * Android 13 and up: ask for the permission, once. If the user has already refused (so the system will no longer
 * show the question), or the permission is fine but notifications are switched off for the app, open the app's
 * notification settings instead. Older Android has no permission, only that switch.
 */
private fun fixNotifications(context: Context, askPermission: () -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        val prefs = context.getSharedPreferences("permission_prompts", Context.MODE_PRIVATE)
        val activity = context.findActivity()
        val asked = prefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false)
        val systemWillAsk = !asked || activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true
        if (systemWillAsk) {
            prefs.edit().putBoolean(KEY_ASKED_NOTIFICATIONS, true).apply()
            askPermission()
            return
        }
    }
    context.openAppNotificationSettings()
}

private const val KEY_ASKED_NOTIFICATIONS = "asked_notifications"

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
