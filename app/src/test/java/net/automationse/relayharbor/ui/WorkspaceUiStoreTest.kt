package net.automationse.relayharbor.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Only safe known screen/filter values are restored from old/corrupt prefs. */
class WorkspaceUiStoreTest {
    @Test fun restoresKnownTabs() {
        listOf("home", "channels", "activity", "integrations", "settings", "chat")
            .forEach { assertEquals(it, WorkspaceUiStore.safePage(it)) }
        assertEquals("home", WorkspaceUiStore.safePage(null))
        assertEquals("home", WorkspaceUiStore.safePage("unknown"))
        assertEquals("home", WorkspaceUiStore.safePage("../login"))
    }

    @Test fun restoresKnownFiltersWithoutLeakingBetweenScreens() {
        assertEquals("recent", WorkspaceUiStore.channelFilter("recent"))
        assertEquals("unread", WorkspaceUiStore.channelFilter("unread"))
        assertEquals("all", WorkspaceUiStore.channelFilter("read"))
        assertEquals("read", WorkspaceUiStore.activityFilter("read"))
        assertEquals("all", WorkspaceUiStore.activityFilter("recent"))
        assertEquals("rss", WorkspaceUiStore.integrationFilter("rss"))
        assertEquals("webhook", WorkspaceUiStore.integrationFilter("webhook"))
        assertEquals("active", WorkspaceUiStore.integrationFilter("active"))
        assertEquals("all", WorkspaceUiStore.integrationFilter("unrecognized"))
    }
}
