package net.automationse.relayharbor.ui

import net.automationse.relayharbor.data.ChannelEntity
import net.automationse.relayharbor.integration.IntegrationConnection
import net.automationse.relayharbor.integration.LocalWebsiteWatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegrationListPolicyTest {
    private val channel = ChannelEntity(id = 7L, name = "変更通知")
    private val local = LocalWebsiteWatch(
        id = "watch-42",
        name = "採用ページ",
        url = "https://example.org/jobs",
        channelId = channel.id,
        intervalMinutes = 30,
        selector = "#new-jobs",
        keyword = "募集",
        lastChecked = 1700000000000,
        lastSuccess = 1699999000000,
        lastError = "",
        enabled = true
    )
    private val remote = IntegrationConnection(
        id = "a1b2c3", name = "ニュース", provider = "website",
        channelName = "ニュース", path = "", enabled = true,
        minScore = 0, keyword = "",
        feedUrl = "https://example.org/news"
    )

    @Test fun localWatchMapsToFullSharedCardWithStableIdentity() {
        val mapped = IntegrationListPolicy.fromLocal(local, listOf(channel))
        assertEquals("device-website:watch-42", mapped.id)
        assertEquals("watch-42", IntegrationListPolicy.localWatchId(mapped))
        assertEquals("変更通知", mapped.channelName)
        assertEquals("採用ページ", mapped.name)
        assertEquals("#new-jobs", mapped.cssSelector)
        assertEquals("募集", mapped.keyword)
        assertEquals(30, mapped.pollIntervalMinutes)
        assertEquals(local.url, mapped.feedUrl)
        assertEquals(local.lastSuccess, mapped.lastSuccess)
        assertTrue(IntegrationListPolicy.isWebsite(mapped))
        assertTrue(IntegrationListPolicy.isPullSource(mapped))
        assertEquals("Web監視・端末内", IntegrationListPolicy.typeLabel(mapped))
        assertTrue(IntegrationListPolicy.detailLabel(mapped).contains("この端末"))
    }

    @Test fun unifiedFiltersCombineRelayAndDeviceWebMonitors() {
        val localConnection = IntegrationListPolicy.fromLocal(local, listOf(channel))
        val connections = listOf(localConnection, remote,
            remote.copy(id = "another", name = "RSS", provider = "rss"))
        assertEquals(3, connections.count {
            IntegrationListPolicy.matches(it, "all", "")
        })
        assertEquals(2, connections.count {
            IntegrationListPolicy.matches(it, "website", "")
        })
        assertEquals(1, connections.count {
            IntegrationListPolicy.matches(it, "local", "")
        })
        assertEquals(1, connections.count {
            IntegrationListPolicy.matches(it, "rss", "")
        })
        assertTrue(IntegrationListPolicy.matches(localConnection, "local", "募集"))
        assertTrue(IntegrationListPolicy.matches(localConnection, "website", "#new-jobs"))
        assertFalse(IntegrationListPolicy.matches(remote, "local", ""))
        assertFalse(IntegrationListPolicy.matches(localConnection, "website", "not-present"))
        assertFalse(IntegrationListPolicy.matches(localConnection.copy(enabled = false), "active", ""))
    }

    @Test fun localActionsCannotMistakeRemoteConnectionForDeviceWatch() {
        val mapped = IntegrationListPolicy.fromLocal(local, listOf(channel))
        assertNull(IntegrationListPolicy.localWatchId(remote))
        assertNull(IntegrationListPolicy.localWatchId(remote.copy(
            id = mapped.id, provider = "website"
        )))
        assertNull(IntegrationListPolicy.localWatchId(mapped.copy(id = "invalid")))
        assertTrue(IntegrationListPolicy.isWebsite(remote))
        assertTrue(IntegrationListPolicy.isPullSource(remote))
        assertEquals("Web監視・Relay", IntegrationListPolicy.typeLabel(remote))
    }

    @Test fun missingChannelIsClearlyIdentifiedForEditAction() {
        val mapped = IntegrationListPolicy.fromLocal(local, emptyList())
        assertEquals("削除されたチャンネル", mapped.channelName)
        assertFalse(mapped.channelName.isBlank())
    }
}
