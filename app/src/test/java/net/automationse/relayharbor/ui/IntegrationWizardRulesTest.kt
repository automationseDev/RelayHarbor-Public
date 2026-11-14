package net.automationse.relayharbor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegrationWizardRulesTest {
    @Test fun allPresetsMapToSupportedBackendTypes() {
        assertEquals(
            listOf("webhook", "rss", "website", "github", "dify", "n8n"),
            integrationWizardPresets.map { it.key }
        )
        assertTrue(
            integrationWizardPresets.all {
                it.provider in setOf("webhook", "github_actions", "rss", "website")
            }
        )
    }

    @Test fun requiredFieldsAndLengthAreChecked() {
        assertTrue(IntegrationWizardRules.validBasics("Lancers AI Scout", "AI案件スカウト"))
        assertFalse(IntegrationWizardRules.validBasics("   ", "channel"))
        assertFalse(IntegrationWizardRules.validBasics("app", "   "))
        assertFalse(IntegrationWizardRules.validBasics("n".repeat(61), "channel"))
        assertFalse(IntegrationWizardRules.validBasics("app", "c".repeat(61)))
    }

    @Test fun rssRequiresSafePublicHttpsUrl() {
        val approved = listOf(
            "https://example.org/feed.xml",
            "https://example.org:443/rss?q=abc"
        )
        val invalid = listOf(
            "", "http://example.org/feed", "https://localhost/feed",
            "https://nas.local/rss", "https://192.168.1.10/rss",
            "https://example.org:8080/rss", "https://user:password@example.org/rss",
            "https://example.org/rss#fragment", "file:///tmp/feed.xml",
            "https://example.org/" + "x".repeat(2100)
        )
        approved.forEach { assertTrue("Should accept: " + it, IntegrationWizardRules.validRssUrl(it)) }
        invalid.forEach { assertFalse("Should reject: " + it, IntegrationWizardRules.validRssUrl(it)) }
    }

    @Test fun websiteMonitorRequiresPublicHttpsAndRejectsInvalidSelector() {
        assertTrue(IntegrationWizardRules.validForReview(
            "サイト更新", "Web更新", "website", "https://example.org/news", "#news"
        ))
        assertTrue(IntegrationWizardRules.validForReview(
            "サイト更新", "Web更新", "website", "https://example.org/news"
        ))
        assertFalse(IntegrationWizardRules.validForReview(
            "サイト更新", "Web更新", "website", "http://example.org/news", "#news"
        ))
        assertFalse(IntegrationWizardRules.validForReview(
            "サイト更新", "Web更新", "website", "https://localhost/news", "#news"
        ))
        assertFalse(IntegrationWizardRules.validForReview(
            "サイト更新", "Web更新", "website", "https://example.org/news",
            "a".repeat(201)
        ))
    }

    @Test fun webhookAndDifyTemplatesDoNotNeedRssUrl() {
        assertTrue(IntegrationWizardRules.validForReview("n8n通知", "自動化", "webhook", ""))
        assertTrue(IntegrationWizardRules.validForReview("GitHub", "GitHub", "github_actions", ""))
        assertFalse(IntegrationWizardRules.validForReview("RSS", "news", "rss", ""))
        assertTrue(
            IntegrationWizardRules.validForReview(
                "RSS", "news", "rss", "https://example.org/feed"
            )
        )
    }
}
