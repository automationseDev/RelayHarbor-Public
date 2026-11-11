package net.automationse.relayharbor.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.MessageDigest
import kotlinx.coroutines.flow.first

private val Context.relayConversations by preferencesDataStore(name = "relay_conversations")

/** Dify conversation IDs are scoped by gateway, provider and channel. */
class RelayConversationStore(private val context: Context) {
    private fun key(endpoint: String, provider: String, channelId: Long) = stringPreferencesKey(
        "conversation_" + MessageDigest.getInstance("SHA-256")
            .digest("$endpoint|$provider|$channelId".toByteArray())
            .joinToString("") { "%02x".format(it) }
    )

    suspend fun get(endpoint: String, provider: String, channelId: Long): String =
        context.relayConversations.data.first()[key(endpoint, provider, channelId)].orEmpty()

    suspend fun save(endpoint: String, provider: String, channelId: Long, conversationId: String) {
        context.relayConversations.edit {
            it[key(endpoint, provider, channelId)] = conversationId
        }
    }
}
