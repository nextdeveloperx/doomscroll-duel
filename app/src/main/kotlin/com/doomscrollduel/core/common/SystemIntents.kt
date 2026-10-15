package com.doomscrollduel.core.common

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.net.Uri
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import com.doomscrollduel.tracking.health.OemFamily

/**
 * Opens the system Accessibility page, where the user turns the reel counter on. Asks the Settings app (via the
 * fragment-args extras most Android builds honour, AOSP included) to land straight on our service's own row instead
 * of the general list, so turning it on is one tap once Settings opens. Ignored harmlessly where a maker's Settings
 * does not support it: the general list still opens.
 */
fun Context.openAccessibilitySettings() {
    val service = ComponentName(packageName, "com.doomscrollduel.tracking.service.ReelAccessibilityService").flattenToString()
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        putExtra(":settings:fragment_args_key", service)
        putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", service) })
    }
    startSafely(intent)
}

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
        OemFamily.MOTOROLA -> listOf(
            "com.motorola.batterycare" to "com.motorola.batterycare.smartbackground.activity.BackgroundSettingActivityAlias",
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

/** Opens this app's subscription page in Google Play, where the person can cancel, resume or fix the payment. */
fun Context.openSubscriptionManagement() =
    startSafely(
        Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions?sku=${com.doomscrollduel.domain.billing.ProProduct.PRODUCT_ID}&package=$packageName")),
    )

/** Opens a public web page (privacy policy, terms) in the browser. Does nothing if the phone has no browser. */
fun Context.openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No browser installed: nothing sensible to open.
    }
}
