package net.automationse.relayharbor.integration

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class IntegrationConnection(
    val id: String,
    val name: String,
    val provider: String,
    val channelName: String,
    val path: String,
    val enabled: Boolean,
    val minScore: Int,
    val keyword: String,
    val feedUrl: String = "",
    val pollIntervalMinutes: Int = 30,
    val lastChecked: Long = 0,
    val lastSuccess: Long = 0,
    val lastError: String = ""
)

data class IntegrationCredential(
    val connection: IntegrationConnection,
    val token: String
)

class IntegrationClient(private val relay: RelayClient = RelayClient()) {
    private fun decode(obj: JsonObject): IntegrationConnection = IntegrationConnection(
        id = obj.getValue("id").jsonPrimitive.content,
        name = obj.getValue("name").jsonPrimitive.content,
        provider = obj.getValue("provider").jsonPrimitive.content,
        channelName = obj.getValue("channel_name").jsonPrimitive.content,
        path = obj.getValue("path").jsonPrimitive.content,
        enabled = obj.getValue("enabled").jsonPrimitive.booleanOrNull == true,
        minScore = obj.getValue("min_score").jsonPrimitive.intOrNull ?: 0,
        keyword = obj.getValue("keyword").jsonPrimitive.content,
        feedUrl = obj["feed_url"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        pollIntervalMinutes = obj["poll_interval_minutes"]?.jsonPrimitive?.intOrNull ?: 30,
        lastChecked = obj["last_checked"]?.jsonPrimitive?.longOrNull ?: 0,
        lastSuccess = obj["last_success"]?.jsonPrimitive?.longOrNull ?: 0,
        lastError = obj["last_error"]?.jsonPrimitive?.contentOrNull.orEmpty()
    )

    suspend fun list(endpoint: String, clientToken: String): List<IntegrationConnection> {
        val json = Json.parseToJsonElement(
            relay.execute(endpoint, clientToken, "/v1/connections")
        ).jsonObject
        return json.getValue("connections").jsonArray.map { decode(it.jsonObject) }
    }

    suspend fun create(
        endpoint: String,
        clientToken: String,
        name: String,
        provider: String,
        channel: String,
        minScore: Int,
        keyword: String,
        feedUrl: String,
        pollIntervalMinutes: Int
    ): IntegrationCredential {
        require(provider in setOf("webhook", "github_actions", "rss"))
        if (provider == "rss") {
            val health = Json.parseToJsonElement(
                relay.execute(endpoint, clientToken, "/healthz")
            ).jsonObject
            val serverVersion = health["version"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (AppUpdater.isNewer("0.5.0", serverVersion)) {
                error("RSS登録にはRelayサーバーv0.5.0以上が必要です。現在のサーバー: v$serverVersion。NASでstableを更新してください")
            }
        }
        val payload = buildJsonObject {
            put("name", name.trim())
            put("provider", provider)
            put("channel_name", channel.trim())
            put("min_score", minScore)
            put("keyword", keyword.trim())
            if (provider == "rss") {
                put("feed_url", feedUrl.trim())
                put("poll_interval_minutes", pollIntervalMinutes)
            }
        }
        val response = Json.parseToJsonElement(
            relay.execute(endpoint, clientToken, "/v1/connections", payload.toString())
        ).jsonObject
        return IntegrationCredential(
            decode(response), response["token"]?.jsonPrimitive?.contentOrNull.orEmpty()
        )
    }

    suspend fun update(
        endpoint: String, clientToken: String, connection: IntegrationConnection
    ): IntegrationConnection {
        val payload = buildJsonObject {
            put("name", connection.name.trim())
            put("channel_name", connection.channelName.trim())
            put("enabled", connection.enabled)
            put("min_score", connection.minScore)
            put("keyword", connection.keyword.trim())
            if (connection.provider == "rss") {
                put("feed_url", connection.feedUrl.trim())
                put("poll_interval_minutes", connection.pollIntervalMinutes)
            }
        }
        val response = relay.execute(
            endpoint, clientToken,
            "/v1/connections/${connection.id}/update", payload.toString()
        )
        return decode(Json.parseToJsonElement(response).jsonObject)
    }

    suspend fun refreshRss(endpoint: String, clientToken: String, connectionId: String): Int {
        val response = relay.execute(
            endpoint, clientToken, "/v1/connections/$connectionId/refresh", "{}"
        )
        return Json.parseToJsonElement(response)
            .jsonObject.getValue("new").jsonPrimitive.intOrNull ?: 0
    }

    suspend fun sendTest(endpoint: String, clientToken: String, connectionId: String): Long {
        val response = relay.execute(
            endpoint, clientToken, "/v1/connections/$connectionId/test", "{}"
        )
        return Json.parseToJsonElement(response)
            .jsonObject.getValue("sequence").jsonPrimitive.content.toLong()
    }

    suspend fun rotate(
        endpoint: String, clientToken: String, connectionId: String
    ): IntegrationCredential {
        val response = relay.execute(
            endpoint, clientToken, "/v1/connections/$connectionId/rotate", "{}"
        )
        val json = Json.parseToJsonElement(response).jsonObject
        return IntegrationCredential(decode(json), json.getValue("token").jsonPrimitive.content)
    }

    suspend fun delete(endpoint: String, clientToken: String, connectionId: String) {
        relay.execute(endpoint, clientToken, "/v1/connections/$connectionId/delete", "{}")
    }
}
