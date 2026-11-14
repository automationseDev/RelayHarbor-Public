package net.automationse.relayharbor.ui

import net.automationse.relayharbor.data.ChannelEntity
import net.automationse.relayharbor.integration.IntegrationConnection
import net.automationse.relayharbor.integration.LocalWebsiteWatch

/**
 * One display model for Relay and device-local integrations.
 *
 * The provider and reserved ID prefix explicitly distinguish local watchers:
 * none of their actions may call a Relay API or require a Relay token.
 */
internal object IntegrationListPolicy {
    const val LOCAL_WEBSITE = "website_local"
    private const val LOCAL_ID_PREFIX = "device-website:"

    fun fromLocal(watch: LocalWebsiteWatch, channels: List<ChannelEntity>): IntegrationConnection =
        IntegrationConnection(
            id = LOCAL_ID_PREFIX + watch.id,
            name = watch.name,
            provider = LOCAL_WEBSITE,
            channelName = channels.firstOrNull { it.id == watch.channelId }?.name
                ?: "削除されたチャンネル",
            path = "",
            enabled = watch.enabled,
            minScore = 0,
            keyword = watch.keyword,
            feedUrl = watch.url,
            cssSelector = watch.selector,
            pollIntervalMinutes = watch.intervalMinutes,
            lastChecked = watch.lastChecked,
            lastSuccess = watch.lastSuccess,
            lastError = watch.lastError
        )

    fun localWatchId(connection: IntegrationConnection): String? =
        if (connection.provider == LOCAL_WEBSITE &&
            connection.id.startsWith(LOCAL_ID_PREFIX) &&
            connection.id.length > LOCAL_ID_PREFIX.length
        ) connection.id.removePrefix(LOCAL_ID_PREFIX)
        else null

    fun isWebsite(connection: IntegrationConnection): Boolean =
        connection.provider in setOf("website", LOCAL_WEBSITE)

    fun isPullSource(connection: IntegrationConnection): Boolean =
        connection.provider in setOf("rss", "website", LOCAL_WEBSITE")

    fun matches(connection: IntegrationConnection, filter: String, query: String): Boolean {
        val needle = query.trim()
        val found = needle.isEmpty() ||
            connection.name.contains(needle, ignoreCase = true) ||
            connection.channelName.contains(needle, ignoreCase = true) ||
            connection.keyword.contains(needle, ignoreCase = true) ||
            connection.feedUrl.contains(needle, ignoreCase = true) ||
            connection.cssSelector.contains(needle, ignoreCase = true)
        return found && when (filter) {
            "rss" -> connection.provider == "rss"
            "website" -> isWebsite(connection)
            "local" -> connection.provider == LOCAL_WEBSITE
            "webhook" -> connection.provider in setOf("webhook", "github_actions")
            "active" -> connection.enabled
            else -> true
        }
    }

    fun typeLabel(connection: IntegrationConnection): String = when (connection.provider) {
        "rss" -> "RSS"
        "website" -> "Web監視・Relay"
        LOCAL_WEBSITE -> "Web監視・端末内"
        "github_actions" -> "GitHub"
        else -> "Webhook"
    }

    fun detailLabel(connection: IntegrationConnection): String = when (connection.provider) {
        "rss" -> "RSS / Atom フィード · Relay"
        "website" -> "Webサイト更新監視 · Relay"
        LOCAL_WEBSITE -> "Webサイト更新監視 · この端末"
        "github_actions" -> "GitHub Actions JSON Webhook"
        else -> "カスタムWebhook"
    }
}
