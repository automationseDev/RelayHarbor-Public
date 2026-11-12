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
    @Test fun displayAndSyncSettingsRejectUnknownValues() {
        assertEquals("normal", WorkspaceUiStore.integrationCardMode(null))
        assertEquals("normal", WorkspaceUiStore.integrationCardMode("unexpected"))
        assertEquals("compact", WorkspaceUiStore.integrationCardMode("compact"))
        assertEquals(30, WorkspaceUiStore.safeForegroundSyncSeconds(0))
        assertEquals(30, WorkspaceUiStore.safeForegroundSyncSeconds(-1))
        assertEquals(30, WorkspaceUiStore.safeForegroundSyncSeconds(15))
        assertEquals(30, WorkspaceUiStore.safeForegroundSyncSeconds(30))
        assertEquals(60, WorkspaceUiStore.safeForegroundSyncSeconds(60))
        assertEquals(120, WorkspaceUiStore.safeForegroundSyncSeconds(120))
        assertEquals(300, WorkspaceUiStore.safeForegroundSyncSeconds(300))
        assertEquals(30, WorkspaceUiStore.safeForegroundSyncSeconds(86400))
    }

}
