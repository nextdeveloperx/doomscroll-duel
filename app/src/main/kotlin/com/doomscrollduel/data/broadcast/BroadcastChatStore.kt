package com.doomscrollduel.data.broadcast

import android.content.Context
import com.doomscrollduel.domain.social.ChatMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

/**
 * Broadcast chat lives only here, in this phone's private storage. It is never sent to a server or saved in the database:
 * the lines travel phone to phone while the room is open, and each phone keeps its own copy. Only the last [MAX_LINES]
 * lines of each room are kept.
 */
@Singleton
class BroadcastChatStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("broadcast_chat", Context.MODE_PRIVATE)

    fun load(roomId: String): List<ChatMessage> {
        val raw = prefs.getString(key(roomId), null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                ChatMessage(
                    id = o.getLong("id"),
                    fromUid = o.getString("uid"),
                    fromName = o.getString("name"),
                    text = o.getString("text"),
                    atMs = o.getLong("at"),
                    mine = o.getBoolean("mine"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(roomId: String, messages: List<ChatMessage>) {
        val array = JSONArray()
        messages.takeLast(MAX_LINES).forEach { m ->
            array.put(
                JSONObject()
                    .put("id", m.id).put("uid", m.fromUid).put("name", m.fromName)
                    .put("text", m.text).put("at", m.atMs).put("mine", m.mine),
            )
        }
        prefs.edit().putString(key(roomId), array.toString()).apply()
    }

    /** Wipes every room's chat from this phone. */
    fun clearAll() = prefs.edit().clear().apply()

    private fun key(roomId: String) = "room:$roomId"

    private companion object {
        const val MAX_LINES = 300
    }
}
