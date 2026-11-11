package net.automationse.relayharbor.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
    val url: String? = null
)

object CardCodec {
    fun encode(card: RichCard): String = buildJsonObject {
        put("schemaVersion", 1)
        put("title", card.title)
        put("description", card.description)
        put("score", card.score?.coerceIn(0, 100))
        put("badge", card.badge)
        put("url", card.url)
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
            metrics = metrics
        )
    }.getOrNull()
}
