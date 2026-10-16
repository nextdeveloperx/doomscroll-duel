package com.doomscrollduel.data.social

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging

/**
 * "Tell me when somebody goes live in Broadcast". On by default. It decides both the phone notification raised while the app runs
 * (see InboxNotifier) and whether this phone listens to the push topic that reaches it when the app is closed.
 */
object BroadcastAlertPrefs {
    /** The push topic every phone with the switch on listens to. */
    const val TOPIC = "broadcasts"

    private const val FILE = "broadcast_alert"
    private const val KEY = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).apply()
        syncTopic(enabled)
    }

    /** Joins or leaves the push topic. Failures are ignored: the next app start tries again. */
    fun syncTopic(enabled: Boolean) {
        val messaging = runCatching { FirebaseMessaging.getInstance() }.getOrNull() ?: return
        if (enabled) messaging.subscribeToTopic(TOPIC) else messaging.unsubscribeFromTopic(TOPIC)
    }
}
