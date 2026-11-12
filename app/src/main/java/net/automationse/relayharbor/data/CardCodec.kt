package net.automationse.relayharbor.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class CardMetric(val label: String, val percent: Int)

data class RichCard(
    val title: String,
    val description: String = "",
    val score: Int? = null,
    val badge: String = "",
    val metrics: List<CardMetric> = emptyList(),
    val url: String? = null,
    val thumbnailUrl: String? = null
)

/** Strict, secret-free HTTPS image link validation; media URLs are never trusted as code. */
object PublicThumbnailUrl {
    fun validate(value: String?): String? {
        val url = value?.trim()?.takeIf { it.length in 1..2048 } ?: return null
        if (url.any { it.code < 32 || it.code == 127 }) return null
        return runCatching {
            val uri = java.net.URI(url)
            val host = uri.host?.lowercase()?.trimEnd('.') ?: return@runCatching null
            if (!uri.scheme.equals("https", ignoreCase = true) ||
                uri.rawUserInfo != null || uri.rawFragment != null ||
                uri.port !in listOf(-1, 443) ||
                host == "localhost" ||
                host.endsWith(".localhost") || host.endsWith(".local") ||
                host.endsWith(".internal") || host.endsWith(".home.arpa") ||
                host.endsWith(".test") || host.endsWith(".invalid") ||
                !host.contains('.') || host.startsWith('.') || host.contains("..") ||
                host.all { it.isDigit() || it == '.' } ||
                host.contains(':')
            ) null else url
        }.getOrNull()
    }
}

object CardCodec {
    private fun candidate(element: JsonElement?): String? {
        return when (element) {
            is JsonObject -> element["url"]?.let { candidate(it) }
                ?: element["src"]?.let { candidate(it) }
            else -> runCatching { element?.jsonPrimitive?.contentOrNull }.getOrNull()
        }
    }

    private fun extractThumbnail(obj: JsonObject): String? {
        val keys = listOf("thumbnail_url", "thumbnailUrl", "image_url", "imageUrl",
            "thumbnail", "image")
        for (key in keys) {
            val validated = PublicThumbnailUrl.validate(candidate(obj[key]))
            if (validated != null) return validated
        }
        val images = obj["images"] as? JsonArray
        return images?.take(5)?.firstNotNullOfOrNull {
            PublicThumbnailUrl.validate(candidate(it))
        }
    }

    fun encode(card: RichCard): String = buildJsonObject {
        put("schemaVersion", 1)
        put("title", card.title)
        put("description", card.description)
        put("score", card.score?.coerceIn(0, 100))
        put("badge", card.badge)
        put("url", card.url)
        card.thumbnailUrl?.let { url ->
            PublicThumbnailUrl.validate(url)?.let { put("thumbnail_url", it) }
        }
        put("metrics", buildJsonArray {
            card.metrics.forEach { metric ->
                add(buildJsonObject {
                    put("label", metric.label)
                    put("percent", metric.percent.coerceIn(0, 100))
                })
            }
        })
    }.toString()

    fun decode(raw: String): RichCard? = runCatching {
        val obj = Json.parseToJsonElement(raw).jsonObject
        val title = obj["title"]?.jsonPrimitive?.content.orEmpty().take(140)
        if (title.isBlank()) return@runCatching null
        val rawMetrics = obj["metrics"] as? JsonArray
        val metrics = rawMetrics?.take(10)?.mapNotNull { entry ->
            runCatching {
                val metric = entry.jsonObject
                val label = metric["label"]?.jsonPrimitive?.content.orEmpty().take(60)
                val percent = metric["percent"]?.jsonPrimitive?.intOrNull ?: return@runCatching null
                if (label.isBlank()) null else CardMetric(label, percent.coerceIn(0, 100))
            }.getOrNull()
        }.orEmpty()
        RichCard(
            title = title,
            description = obj["description"]?.jsonPrimitive?.content.orEmpty().take(2000),
            score = obj["score"]?.let { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() }
                ?.coerceIn(0, 100),
            badge = obj["badge"]?.jsonPrimitive?.content.orEmpty().take(50),
            url = obj["url"]?.jsonPrimitive?.content?.let { target ->
                runCatching {
                    val uri = java.net.URI(target)
                    if (uri.scheme?.lowercase() in listOf("https", "http") &&
                        !uri.host.isNullOrBlank() && target.length <= 2048
                    ) target else null
                }.getOrNull()
            },
            metrics = metrics,
            thumbnailUrl = extractThumbnail(obj)
        )
    }.getOrNull()
}
