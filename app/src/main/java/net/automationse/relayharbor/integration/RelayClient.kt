package net.automationse.relayharbor.integration

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

data class IncomingRelayEvent(
    val sequence: Long,
    val channel: String,
    val sender: String,
    val text: String,
    val cardJson: String?,
    val timestamp: Long
)

data class RelayChatTurn(val role: String, val text: String)
data class RelayChatAnswer(val answer: String, val conversationId: String)

class RelayClient {
    companion object {
        fun validEndpoint(value: String): Boolean = runCatching {
            val uri = URI(value.trim())
            uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrBlank() &&
                uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null
        }.getOrDefault(false)
    }

    internal suspend fun execute(
        endpoint: String,
        token: String,
        route: String,
        body: String? = null
    ): String = withContext(Dispatchers.IO) {
        require(validEndpoint(endpoint)) { "HTTPSの接続先を設定してください" }
        require(token.isNotBlank()) { "接続トークンを設定してください" }
        val base = endpoint.trim().trimEnd('/')
        val url = URL(base + route)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = if (body == null) "GET" else "POST"
            connectTimeout = 10000
            readTimeout = 95000
            instanceFollowRedirects = false
            setRequestProperty("Authorization", "Bearer " + token)
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = conn.responseCode
            if (status !in 200..299) {
                val errorBody = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use {
                    it.readText().take(8_192)
                }.orEmpty()
                val detail = runCatching {
                    val raw = Json.parseToJsonElement(errorBody).jsonObject["detail"]
                    when (raw) {
                        is JsonArray -> raw.take(3).mapNotNull { item ->
                            (item as? JsonObject)?.get("msg")?.jsonPrimitive?.contentOrNull
                        }.joinToString(" / ")
                        else -> raw?.jsonPrimitive?.contentOrNull
                    }
                }.getOrNull()?.take(320)
                val guidance = when (status) {
                    401 -> "接続トークンが一致していません"
                    403 -> "アクセスが拒否されました"
                    422 -> "登録内容またはサーバーのAPIバージョンを確認してください"
                    404 -> "指定APIがありません。サーバーのバージョンを確認してください"
                    502 -> "RelayサーバーまたはCloudflare Tunnelへの接続を確認してください"
                    else -> "Relayサーバーからエラーが返されました"
                }
                throw IllegalStateException("Relay HTTP $status：${detail?.takeIf { it.isNotBlank() } ?: guidance}")
            }
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val response = StringBuilder()
                val buffer = CharArray(4096)
                var size = 0
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    size += count
                    require(size <= 524288) { "サーバーの応答サイズが大きすぎます" }
                    response.append(buffer, 0, count)
                }
                response.toString()
            }
            text
        } finally {
            conn.disconnect()
        }
    }

    suspend fun loadEvents(
        endpoint: String,
        token: String,
        after: Long
    ): List<IncomingRelayEvent> {
        val raw = execute(endpoint, token, "/v1/events?after=$after&limit=100")
        val root = Json.parseToJsonElement(raw).jsonObject
        val events = root["events"] as? JsonArray ?: return emptyList()
        return events.map { item ->
            val obj = item.jsonObject
            IncomingRelayEvent(
                sequence = obj.getValue("sequence").jsonPrimitive.longOrNull
                    ?: error("sequenceがありません"),
                channel = obj.getValue("channel").jsonPrimitive.content.take(60),
                sender = obj.getValue("sender").jsonPrimitive.content.take(80),
                text = obj["text"]?.jsonPrimitive?.contentOrNull?.take(10000).orEmpty(),
                cardJson = obj["card"]?.takeUnless { it.toString() == "null" }?.toString(),
                timestamp = obj.getValue("timestamp").jsonPrimitive.longOrNull
                    ?: System.currentTimeMillis()
            )
        }
    }

    suspend fun sendChat(
        endpoint: String,
        token: String,
        provider: String,
        message: String,
        conversationId: String?,
        history: List<RelayChatTurn>
    ): RelayChatAnswer {
        require(provider in setOf("openai", "dify"))
        val body = buildJsonObject {
            put("provider", provider)
            put("message", message)
            put("conversation_id", conversationId ?: "")
            put("history", buildJsonArray {
                history.takeLast(12).forEach { turn ->
                    add(buildJsonObject {
                        put("role", turn.role)
                        put("content", turn.text.take(12000))
                    })
                }
            })
        }.toString()
        val response = execute(endpoint, token, "/v1/chat", body)
        val json = Json.parseToJsonElement(response).jsonObject
        return RelayChatAnswer(
            answer = json.getValue("answer").jsonPrimitive.content,
            conversationId = json["conversation_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        )
    }
}
