package net.automationse.relayharbor.integration

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class AiProfileRulesTest {
    @Test fun validOpenAiAndDifyProfiles() {
        val draft = AiProfileDraft(
            "API profile", "openai", "https://api.openai.com/v1",
            "gpt-4.1-mini", "test-secret"
        )
        assertTrue(AiProfileRules.valid(draft, true))
        assertFalse(AiProfileRules.valid(draft.copy(apiKey = ""), true))
        assertTrue(AiProfileRules.valid(draft.copy(apiKey = ""), false))
        assertTrue(AiProfileRules.valid(draft.copy(kind = "dify", model = ""), true))
    }

    @Test fun missingModelAndHttpAreRejected() {
        val draft = AiProfileDraft(
            "My model", "compatible", "https://api.example.com/v1", "", "secret"
        )
        assertFalse(AiProfileRules.valid(draft, true))
        assertFalse(AiProfileRules.valid(draft.copy(model = "model", baseUrl = "http://x.test"), true))
        assertFalse(AiProfileRules.valid(draft.copy(model = "model", kind = "unknown"), true))
        assertEquals("OpenAI互換", AiProfileRules.kindLabel("compatible"))
    }

    @Test fun anthropicAndProviderPresetsAreAvailable() {
        val claude = AiProviderPresets.options.first { it.id == "claude" }
        assertEquals("anthropic", claude.kind)
        assertEquals("https://api.anthropic.com/v1", claude.baseUrl)
        assertTrue(AiProfileRules.valid(AiProfileDraft(
            "Claude", claude.kind, claude.baseUrl, claude.suggestedModel, "test-key"
        ), true))
        assertEquals("claude", AiProviderPresets.guess(claude.kind, claude.baseUrl))
        assertEquals("gemini", AiProviderPresets.guess(
            "compatible", "https://generativelanguage.googleapis.com/v1beta/openai/"
        ))
        assertEquals("custom", AiProviderPresets.guess("compatible", "https://other.example/v1"))
        assertTrue(AiProviderPresets.options.map { it.id }.distinct().size ==
            AiProviderPresets.options.size)
    }
}
