package com.doomscrollduel.tracking.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Brings the keep-alive notification back after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            TrackingKeepAlive.start(context)
        }
    }
}
