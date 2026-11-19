package net.automationse.relayharbor.integration

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.put

data class AiProfile(val id: String, val name: String, val kind: String,
    val baseUrl: String, val model: String, val managed: Boolean)
data class AiProfileList(val profiles: List<AiProfile>, val canManage: Boolean)
data class AiProfileDraft(val name: String, val kind: String, val baseUrl: String,
    val model: String, val apiKey: String)

object AiProfileRules {
    val kinds = setOf("openai", "compatible", "dify", "anthropic")
    fun kindLabel(kind: String): String = when (kind) {
        "dify" -> "Dify"
        "anthropic" -> "Claude (Anthropic)"
        "compatible" -> "OpenAI互換"
        else -> "OpenAI"
    }
    fun valid(draft: AiProfileDraft, requireKey: Boolean): Boolean =
        draft.name.trim().isNotEmpty() &&
            draft.name.length <= 60 &&
            draft.kind in kinds &&
            RelayClient.validEndpoint(draft.baseUrl) &&
            (draft.kind == "dify" || draft.model.trim().isNotEmpty()) &&
            (!requireKey || draft.apiKey.trim().isNotEmpty())
}

/** Provider templates are UI hints. Models are editable because availability and pricing change. */
data class AiProviderPreset(
    val id: String, val label: String, val kind: String,
    val baseUrl: String, val suggestedModel: String
)

object AiProviderPresets {
    val options = listOf(
        AiProviderPreset("openai", "OpenAI", "openai",
            "https://api.openai.com/v1", "gpt-4.1-mini"),
        AiProviderPreset("claude", "Claude (Anthropic)", "anthropic",
            "https://api.anthropic.com/v1", "claude-sonnet-4-6"),
        AiProviderPreset("gemini", "Google Gemini", "compatible",
            "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash"),
        AiProviderPreset("groq", "Groq", "compatible",
            "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
        AiProviderPreset("openrouter", "OpenRouter", "compatible",
            "https://openrouter.ai/api/v1", "openai/gpt-4.1-mini"),
        AiProviderPreset("deepseek", "DeepSeek", "compatible",
            "https://api.deepseek.com", "deepseek-flash"),
        AiProviderPreset("xai", "xAI (Grok)", "compatible",
            "https://api.x.ai/v1", ""),
        AiProviderPreset("mistral", "Mistral", "compatible",
            "https://api.mistral.ai/v1", "mistral-small-latest"),
        AiProviderPreset("dify", "Dify", "dify", "", ""),
        AiProviderPreset("custom", "その他（OpenAI互換）", "compatible", "", "")
    )

    fun guess(kind: String, baseUrl: String): String =
        options.firstOrNull { it.kind == kind &&
            it.baseUrl == baseUrl.trim().trimEnd('/') }?.id ?: "custom"
}

class AiProfileClient(private val relay: RelayClient = RelayClient()) {
    suspend fun list(endpoint: String, token: String): AiProfileList {
        val root = Json.parseToJsonElement(
            relay.execute(endpoint, token, "/v1/ai-profiles")
        ).jsonObject
        val profiles = (root["profiles"] as? JsonArray)?.map { item ->
            val p = item.jsonObject
            AiProfile(
                id = p.getValue("id").jsonPrimitive.content,
                name = p.getValue("name").jsonPrimitive.content,
                kind = p.getValue("kind").jsonPrimitive.content,
                baseUrl = p.getValue("base_url").jsonPrimitive.content,
                model = p["model"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                managed = p["managed"]?.jsonPrimitive?.booleanOrNull == true
            )
        }.orEmpty()
        return AiProfileList(profiles, root["can_manage"]?.jsonPrimitive?.booleanOrNull == true)
    }
    private fun body(draft: AiProfileDraft) = buildJsonObject {
        put("name", draft.name.trim())
        put("kind", draft.kind)
        put("base_url", draft.baseUrl.trim().trimEnd('/'))
        put("model", draft.model.trim())
        // Empty on update keeps the existing encrypted server secret.
        put("api_key", draft.apiKey.trim())
    }.toString()

    suspend fun create(endpoint: String, token: String, draft: AiProfileDraft) {
        require(AiProfileRules.valid(draft, true))
        relay.execute(endpoint, token, "/v1/ai-profiles", body(draft))
    }
    suspend fun update(endpoint: String, token: String, id: String, draft: AiProfileDraft) {
        require(id.matches(Regex("[a-f0-9]{32}")))
        require(AiProfileRules.valid(draft, false))
        relay.execute(endpoint, token, "/v1/ai-profiles/$id/update", body(draft))
    }
    suspend fun delete(endpoint: String, token: String, id: String) {
        require(id.matches(Regex("[a-f0-9]{32}")))
        relay.execute(endpoint, token, "/v1/ai-profiles/$id", method = "DELETE")
    }
}
