package com.doomscrollduel.core.common

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.net.Uri
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.doomscrollduel.tracking.health.OemFamily

/** Opens the system Accessibility page, where the user turns the reel counter on. */
fun Context.openAccessibilitySettings() =
    startSafely(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

/** Opens the system battery-optimization list. Falls back to general settings on OEMs without it. */
fun Context.openBatterySettings() =
    startSafely(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

/**
 * Opens the phone maker's autostart / background-start screen when we know it, and the app's own
 * settings page otherwise. These screens are private to each maker and change between versions, so
 * every candidate is tried and a miss is normal. Returns true when an OEM screen opened.
 */
fun Context.openOemAutostartSettings(oem: OemFamily): Boolean {
    val candidates = when (oem) {
        OemFamily.XIAOMI -> listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        )
        OemFamily.REALME_OPPO -> listOf(
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        )
        OemFamily.VIVO -> listOf(
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        )
        OemFamily.SAMSUNG -> listOf(
            "com.samsung.android.lool" to "com.samsung.android.sm.battery.ui.BatteryActivity",
        )
        OemFamily.OTHER -> emptyList()
    }
    for ((pkg, cls) in candidates) {
        val intent = Intent().setComponent(ComponentName(pkg, cls))
        if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
            return true
        } catch (_: ActivityNotFoundException) {
            // Not on this phone or this software version: try the next one.
        } catch (_: SecurityException) {
            // The screen exists but is not open to other apps.
        }
    }
    startSafely(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
    )
    return false
}

/** The app's own notification settings page, for when notifications are switched off. */
fun Context.openAppNotificationSettings() =
    startSafely(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))

/** Shares plain text through the system share sheet. */
fun Context.shareText(text: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
    startSafely(Intent.createChooser(send, null))
}

private fun Context.startSafely(intent: Intent) {
    if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No handler for this page on this phone: open the main settings list instead.
        val fallback = Intent(Settings.ACTION_SETTINGS)
        if (this !is Activity) fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(fallback) }
    }
}
