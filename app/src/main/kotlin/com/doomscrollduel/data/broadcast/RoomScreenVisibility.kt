package com.doomscrollduel.data.broadcast

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** True while the room page is in front of the person. The floating circle is shown whenever a room is live and this is false. */
object RoomScreenVisibility {
    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible

    fun set(visible: Boolean) {
        _visible.value = visible
    }
}

/** The floating circle is a "display over other apps" window; the person is sent to that switch once, when they first leave a live room. */
object BubblePermission {
    private const val FILE = "broadcast_bubble"
    private const val KEY_ASKED = "asked"

    fun granted(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun shouldAsk(context: Context): Boolean =
        !granted(context) && !context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_ASKED, false)

    fun markAsked(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_ASKED, true).apply()
    }
}
