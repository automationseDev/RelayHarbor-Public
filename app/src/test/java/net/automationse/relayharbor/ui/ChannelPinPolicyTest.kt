package net.automationse.relayharbor.ui

import net.automationse.relayharbor.data.ChannelEntity
import net.automationse.relayharbor.data.ChannelSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelPinPolicyTest {
    private val channels = listOf(
        ChannelEntity(id = 1, name = "GitHub", position = 10),
        ChannelEntity(id = 2, name = "監視", position = 20),
        ChannelEntity(id = 3, name = "AIチャット", position = 30)
    )
    private val summaries = mapOf(
        1L to ChannelSummary(1, "build", "Bot", "text", 100, 0),
        2L to ChannelSummary(2, "alert", "Bot", "text", 200, 3),
        3L to ChannelSummary(3, "answer", "AI", "text", 300, 1)
    )

    @Test fun togglePinsIsReversibleAndIgnoresNonPositiveIds() {
        assertEquals(setOf(2L), ChannelPinPolicy.toggle(emptySet(), 2))
        assertTrue(ChannelPinPolicy.toggle(setOf(2L), 2).isEmpty())
        assertEquals(setOf(2L), ChannelPinPolicy.toggle(setOf(2L), -1))
    }

    @Test fun pinnedChannelsStayAtTopWithoutLosingOthers() {
        val result = ChannelPinPolicy.visible(channels, summaries, "", "all", setOf(3L))
        assertEquals(listOf(3L, 1L, 2L), result.map { it.id })
        assertEquals(channels.size, result.size)
        assertEquals(channels.size, result.map { it.id }.toSet().size)
    }

    @Test fun filtersSupportSearchUnreadPinnedAndRecents() {
        assertEquals(listOf(2L, 3L),
            ChannelPinPolicy.visible(channels, summaries, "", "unread", emptySet()).map { it.id })
        assertEquals(listOf(3L, 2L, 1L),
            ChannelPinPolicy.visible(channels, summaries, "", "recent", emptySet()).map { it.id })
        assertEquals(listOf(2L),
            ChannelPinPolicy.visible(channels, summaries, "", "pinned", setOf(2L)).map { it.id })
        assertEquals(listOf(1L),
            ChannelPinPolicy.visible(channels, summaries, "git", "all", setOf(2L)).map { it.id })
        assertTrue(ChannelPinPolicy.visible(channels, summaries, "unknown", "all", emptySet()).isEmpty())
    }

    @Test fun stalePinnedIdsNeverGeneratePhantomChannels() {
        val result = ChannelPinPolicy.visible(channels, summaries, "", "pinned", setOf(999L))
        assertTrue(result.isEmpty())
        assertFalse(ChannelPinPolicy.visible(channels, summaries, "", "all",
            setOf(999L)).any { it.id == 999L })
    }
}
