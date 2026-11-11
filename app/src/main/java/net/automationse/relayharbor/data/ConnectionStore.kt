package net.automationse.relayharbor.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.connectionStore by preferencesDataStore(name = "connection_settings")

data class ConnectionSettings(
    val mode: String = "local",
    val endpoint: String = "",
    val aiProvider: String = "none"
)

class ConnectionStore(private val context: Context) {
    private val modeKey = stringPreferencesKey("storage_mode")
    private val endpointKey = stringPreferencesKey("api_endpoint")
    private val aiKey = stringPreferencesKey("ai_provider")

    val settings: Flow<ConnectionSettings> = context.connectionStore.data.map { prefs ->
        ConnectionSettings(
            mode = prefs[modeKey] ?: "local",
            endpoint = prefs[endpointKey] ?: "",
            aiProvider = prefs[aiKey] ?: "none"
        )
    }

    suspend fun save(mode: String, endpoint: String, ai: String) {
        require(mode in setOf("local", "cloud", "selfhost"))
        require(ai in setOf("none", "dify", "openai"))
        val safeEndpoint = endpoint.trim()
        require(safeEndpoint.isEmpty() || safeEndpoint.startsWith("https://")) {
            "接続先にはHTTPSのURLを設定してください"
        }
        context.connectionStore.edit { prefs ->
            prefs[modeKey] = mode
            prefs[endpointKey] = safeEndpoint
            prefs[aiKey] = ai
        }
    }
}
