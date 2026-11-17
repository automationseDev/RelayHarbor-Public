package net.automationse.relayharbor.data

import android.content.Context
import java.security.MessageDigest

/** Device-private per-channel choice; never stores provider API keys. */
class AiChannelSelectionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "ai_channel_selection_v1", Context.MODE_PRIVATE
    )
    private fun key(endpoint: String, channelId: Long): String {
        val hashed = MessageDigest.getInstance("SHA-256")
            .digest(endpoint.trim().trimEnd('/').toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "profile_" + hashed + "_" + channelId
    }
    fun get(endpoint: String, channelId: Long): String =
        prefs.getString(key(endpoint, channelId), "default") ?: "default"

    fun set(endpoint: String, channelId: Long, choice: String) {
        if (channelId <= 0) return
        require(choice == "default" || choice == "off" ||
            choice in setOf("legacy:openai", "legacy:dify") ||
            choice.matches(Regex("[a-f0-9]{32}")))
        prefs.edit().putString(key(endpoint, channelId), choice).apply()
    }
}
