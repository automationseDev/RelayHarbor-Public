package net.automationse.relayharbor.data

import android.content.Context

/**
 * Retains unsent text per channel across tab switches and process restarts.
 * The data is private to the phone and never uploaded until Send is tapped.
 */
class ChatDraftStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "chat_message_drafts", Context.MODE_PRIVATE
    )

    fun load(channelId: Long): String =
        preferences.getString("channel_$channelId", "") ?: ""

    fun save(channelId: Long, content: String) {
        val key = "channel_$channelId"
        preferences.edit().apply {
            if (content.isEmpty()) remove(key)
            else putString(key, content.take(8000))
        }.apply()
    }

    fun clear(channelId: Long) {
        preferences.edit().remove("channel_$channelId").apply()
    }
}
