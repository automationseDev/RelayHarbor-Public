package net.automationse.relayharbor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeChromePolicyTest {
    @Test fun allExistingHomePanelsHaveAStableReadableTitle() {
        HomePanelLayout.panelIds.forEach { id ->
            val spec = HomePanelHeaders.spec(id)
            assertTrue("missing title for $id", spec.title.isNotBlank())
        }
        assertEquals(HomePanelLayout.panelIds.size,
            HomePanelLayout.panelIds.map { HomePanelHeaders.spec(it).title }.toSet().size)
    }

    @Test fun onlyInteractiveSectionsHaveHeaderActions() {
        assertEquals("追加・編集", HomePanelHeaders.spec("apps").actionLabel)
        assertEquals("すべて見る", HomePanelHeaders.spec("activity").actionLabel)
        assertEquals("一覧へ", HomePanelHeaders.spec("channels").actionLabel)
        listOf("overview", "drawer", "sync").forEach {
            assertEquals(null, HomePanelHeaders.spec(it).actionLabel)
        }
    }

    @Test fun sixTabsUseSelectedOnlyLabelsOnNarrowScreensOrLargeText() {
        assertFalse(BottomNavigationPolicy.showAllLabels(360, 1.0f))
        assertFalse(BottomNavigationPolicy.showAllLabels(480, 1.0f))
        assertFalse(BottomNavigationPolicy.showAllLabels(599, 1.3f))
        assertTrue(BottomNavigationPolicy.showAllLabels(600, 1.0f))
        assertTrue(BottomNavigationPolicy.showAllLabels(840, 1.2f))
    }

    @Test fun unreadNotificationBadgeDoesNotExpandNavigationLabel() {
        assertEquals("", BottomNavigationPolicy.unreadBadge(0))
        assertEquals("", BottomNavigationPolicy.unreadBadge(-1))
        assertEquals("1", BottomNavigationPolicy.unreadBadge(1))
        assertEquals("99", BottomNavigationPolicy.unreadBadge(99))
        assertEquals("99+", BottomNavigationPolicy.unreadBadge(100))
        assertEquals("99+", BottomNavigationPolicy.unreadBadge(2000))
    }

    @Test fun fixedHeaderDoesNotChangePersistedPanelDimensions() {
        val saved = HomePanelLayout.default(HomePanelLayout.COVER)
        val encoded = HomePanelLayout.encode(HomePanelLayout.COVER, saved)
        assertEquals(saved, HomePanelLayout.decode(HomePanelLayout.COVER, encoded))
    }
}
