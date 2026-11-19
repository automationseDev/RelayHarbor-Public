package net.automationse.relayharbor.integration

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Relay-only usage; never represents a provider's whole account or free quota. */
data class AiUsageTotals(
    val requests: Int = 0,
    val successCount: Int = 0,
    val errorCount: Int = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val unreportedCalls: Int = 0,
    val averageLatencyMs: Int = 0,
    val estimatedUsd: Double? = null,
    val estimatedPartialUsd: Double = 0.0,
    val unpricedModels: Int = 0
)

data class AiUsageModel(
    val profileId: String,
    val profileName: String,
    val kind: String,
    val model: String,
    val requests: Int,
    val successCount: Int,
    val errorCount: Int,
    val inputTokens: Long,
    val outputTokens: Long,
    val unreportedCalls: Int,
    val averageLatencyMs: Int,
    val estimatedUsd: Double?,
    val inputUsdPerMillion: Double?,
    val outputUsdPerMillion: Double?
)

data class AiUsageDay(
    val day: String,
    val requests: Int,
    val errors: Int
)

data class AiUsageSummary(
    val periodDays: Int,
    val trackingSince: Long?,
    val totals: AiUsageTotals,
    val models: List<AiUsageModel>,
    val daily: List<AiUsageDay>
)

object AiUsageRules {
    val periods = listOf(1, 7, 30, 90, 365)

    fun rate(value: String): Double? {
        if (value.isBlank()) return null
        val number = value.trim().toDoubleOrNull() ?: return null
        return number.takeIf { it.isFinite() && it >= 0.0 && it <= 1000.0 }
    }

    fun validRates(input: String, output: String): Boolean =
        rate(input) != null && rate(output) != null
}

private fun JsonObject.int(key: String): Int =
    this[key]?.jsonPrimitive?.intOrNull ?: 0

private fun JsonObject.long(key: String): Long =
    this[key]?.jsonPrimitive?.longOrNull ?: 0L

private fun JsonObject.str(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.number(key: String): Double? =
    this[key]?.jsonPrimitive?.doubleOrNull

class AiUsageClient(private val relay: RelayClient = RelayClient()) {
    suspend fun summary(endpoint: String, token: String, days: Int): AiUsageSummary {
        require(days in AiUsageRules.periods)
        val root = Json.parseToJsonElement(
            relay.execute(endpoint, token, "/v1/ai-usage?days=$days")
        ).jsonObject
        val totalsJson = root.getValue("totals").jsonObject
        val totals = AiUsageTotals(
            requests = totalsJson.int("requests"),
            successCount = totalsJson.int("success_count"),
            errorCount = totalsJson.int("error_count"),
            inputTokens = totalsJson.long("input_tokens"),
            outputTokens = totalsJson.long("output_tokens"),
            unreportedCalls = totalsJson.int("unreported_calls"),
            averageLatencyMs = totalsJson.int("average_latency_ms"),
            estimatedUsd = totalsJson.number("estimated_usd"),
            estimatedPartialUsd = totalsJson.number("estimated_partial_usd") ?: 0.0,
            unpricedModels = totalsJson.int("unpriced_models")
        )
        val models = ((root["models"] as? JsonArray) ?: JsonArray(emptyList())).map { v ->
            val p = v.jsonObject
            AiUsageModel(
                profileId = p.str("profile_id"),
                profileName = p.str("profile_name"),
                kind = p.str("kind"),
                model = p.str("model"),
                requests = p.int("requests"),
                successCount = p.int("success_count"),
                errorCount = p.int("error_count"),
                inputTokens = p.long("input_tokens"),
                outputTokens = p.long("output_tokens"),
                unreportedCalls = p.int("unreported_calls"),
                averageLatencyMs = p.int("average_latency_ms"),
                estimatedUsd = p.number("estimated_usd"),
                inputUsdPerMillion = p.number("input_usd_per_million"),
                outputUsdPerMillion = p.number("output_usd_per_million")
            )
        }
        val daily = ((root["daily"] as? JsonArray) ?: JsonArray(emptyList())).map { v ->
            val p = v.jsonObject
            AiUsageDay(day = p.str("day"),
                requests = p.int("requests"),
                errors = p.int("errors"))
        }
        return AiUsageSummary(
            periodDays = root["period_days"]?.jsonPrimitive?.intOrNull ?: days,
            trackingSince = root["tracking_since"]?.jsonPrimitive?.longOrNull,
            totals = totals, models = models, daily = daily
        )
    }

    suspend fun setRates(endpoint: String, token: String,
                         profileId: String, model: String,
                         inputUsdPerMillion: Double?, outputUsdPerMillion: Double?) {
        require(profileId.isNotBlank())
        require(inputUsdPerMillion == null && outputUsdPerMillion == null ||
                inputUsdPerMillion != null && outputUsdPerMillion != null &&
                inputUsdPerMillion.isFinite() && outputUsdPerMillion.isFinite() &&
                inputUsdPerMillion in 0.0..1000.0 &&
                outputUsdPerMillion in 0.0..1000.0)
        val body = buildJsonObject {
            put("profile_id", profileId)
            put("model", model)
            if (inputUsdPerMillion != null) put("input_usd_per_million", inputUsdPerMillion)
            if (outputUsdPerMillion != null) put("output_usd_per_million", outputUsdPerMillion)
        }.toString()
        relay.execute(endpoint, token, "/v1/ai-usage/rates", body)
    }

    suspend fun exportCsv(endpoint: String, token: String, days: Int): String {
        require(days in AiUsageRules.periods)
        return relay.execute(
            endpoint, token, "/v1/ai-usage/export?days=$days",
            maxResponseBytes = 4_194_304
        )
    }
}
