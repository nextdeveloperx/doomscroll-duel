package com.doomscrollduel.core.common

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Opens the system Accessibility page, where the user turns the reel counter on. */
fun Context.openAccessibilitySettings() =
    startSafely(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

/** Opens the system battery-optimization list. Falls back to general settings on OEMs without it. */
fun Context.openBatterySettings() =
    startSafely(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

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
