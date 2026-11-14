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
    val cssSelector: String = "",
    val pollIntervalMinutes: Int = 30,
    val lastChecked: Long = 0,
    val lastSuccess: Long = 0,
    val lastError: String = ""
)

data class IntegrationCredential(
    val connection: IntegrationConnection,
    val token: String
)

data class IntegrationImportResult(
    val createdCount: Int,
    val skippedCount: Int,
    val credentials: List<IntegrationCredential>
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
        cssSelector = obj["css_selector"]?.jsonPrimitive?.contentOrNull.orEmpty(),
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
        pollIntervalMinutes: Int,
        cssSelector: String = ""
    ): IntegrationCredential {
        require(provider in setOf("webhook", "github_actions", "rss", "website"))
        if (provider == "rss" || provider == "website") {
            val health = Json.parseToJsonElement(
                relay.execute(endpoint, clientToken, "/healthz")
            ).jsonObject
            val serverVersion = health["version"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val minimum = if (provider == "website") "0.6.0" else "0.5.0"
            if (AppUpdater.isNewer(minimum, serverVersion)) {
                error("この連携にはRelayサーバーv$minimum以上が必要です。現在: v$serverVersion。サーバーを更新してください")
            }
        }
        val payload = buildJsonObject {
            put("name", name.trim())
            put("provider", provider)
            put("channel_name", channel.trim())
            put("min_score", minScore)
            put("keyword", keyword.trim())
            if (provider == "rss" || provider == "website") {
                put("feed_url", feedUrl.trim())
                put("poll_interval_minutes", pollIntervalMinutes)
                if (provider == "website") put("css_selector", cssSelector.trim())
            }
        }
        val response = Json.parseToJsonElement(
            relay.execute(endpoint, clientToken, "/v1/connections", payload.toString())
        ).jsonObject
        return IntegrationCredential(
            decode(response), response["token"]?.jsonPrimitive?.contentOrNull.orEmpty()
        )
    }

    suspend fun exportJson(endpoint: String, clientToken: String): String {
        val exported = relay.execute(endpoint, clientToken, "/v1/connections/export")
        val data = Json.parseToJsonElement(exported).jsonObject
        require(data["format"]?.jsonPrimitive?.contentOrNull == "relayharbor-integrations") {
            "連携設定のエクスポート形式が不正です"
        }
        return exported
    }

    suspend fun importJson(
        endpoint: String, clientToken: String, content: String
    ): IntegrationImportResult {
        require(content.toByteArray(Charsets.UTF_8).size <= 256_000) {
            "連携設定ファイルのサイズは256KB以内にしてください"
        }
        val document = Json.parseToJsonElement(content).jsonObject
        require(document["format"]?.jsonPrimitive?.contentOrNull == "relayharbor-integrations" &&
            document["version"]?.jsonPrimitive?.intOrNull == 1 &&
            document["connections"]?.jsonArray != null
        ) { "RelayHarborの連携設定JSON（形式v1）を選択してください" }
        val json = Json.parseToJsonElement(
            relay.execute(endpoint, clientToken, "/v1/connections/import", document.toString())
        ).jsonObject
        val credentials = json.getValue("created").jsonArray.map { item ->
            val obj = item.jsonObject
            IntegrationCredential(
                connection = decode(obj),
                token = obj["token"]?.jsonPrimitive?.contentOrNull.orEmpty()
            )
        }
        return IntegrationImportResult(
            createdCount = json["created_count"]?.jsonPrimitive?.intOrNull ?: 0,
            skippedCount = json["skipped"]?.jsonPrimitive?.intOrNull ?: 0,
            credentials = credentials.filter { it.token.isNotEmpty() }
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
            if (connection.provider == "rss" || connection.provider == "website") {
                put("feed_url", connection.feedUrl.trim())
                put("poll_interval_minutes", connection.pollIntervalMinutes)
                if (connection.provider == "website")
                    put("css_selector", connection.cssSelector.trim())
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
