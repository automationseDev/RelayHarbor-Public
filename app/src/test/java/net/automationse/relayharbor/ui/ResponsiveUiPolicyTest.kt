package net.automationse.relayharbor.ui

import net.automationse.relayharbor.data.MessageEntity
import net.automationse.relayharbor.data.NotificationAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsiveUiPolicyTest {
    @Test fun compactScreensStaySinglePane() {
        assertFalse(ResponsiveLayoutPolicy.useTwoPanes(360))
        assertFalse(ResponsiveLayoutPolicy.useTwoPanes(599))
        assertTrue(ResponsiveLayoutPolicy.useTwoPanes(600))
        assertTrue(ResponsiveLayoutPolicy.useTwoPanes(840))
    }

    @Test fun swipeActionsAreReversibleAndNonDestructive() {
        val message = MessageEntity(id = 10, channelId = 1, sender = "Bot")
        assertEquals(NotificationAction.COMPLETE, NotificationSwipePolicy.right(message))
        assertEquals(NotificationAction.SAVE_FOR_LATER, NotificationSwipePolicy.left(message))
        assertEquals(
            NotificationAction.REOPEN,
            NotificationSwipePolicy.inverse(NotificationAction.COMPLETE)
        )
        assertEquals(
            NotificationAction.REMOVE_FROM_LATER,
            NotificationSwipePolicy.inverse(NotificationAction.SAVE_FOR_LATER)
        )
        assertEquals(
            NotificationAction.COMPLETE,
            NotificationSwipePolicy.right(message.copy(isDone = true))
                .let(NotificationSwipePolicy::inverse)
        )
        assertEquals(
            NotificationAction.REMOVE_FROM_LATER,
            NotificationSwipePolicy.left(message.copy(savedForLater = true))
        )
    }

    @Test fun cardDensitySupportsInboxChatAndInspector() {
        assertEquals(3, CardDensity.values().size)
        assertTrue(CardDensity.values().toSet().containsAll(
            listOf(CardDensity.COMPACT, CardDensity.STANDARD, CardDensity.DETAILED)
        ))
    }

    @Test fun homeGridAutoPacksNarrowCardsWithoutDuplicating() {
        val layout = listOf(
            HomePanel("overview", 3, 1),
            HomePanel("drawer", 3, 1),
            HomePanel("apps", 1, 3),
            HomePanel("sync", 1, 2),
            HomePanel("activity", 2, 3),
            HomePanel("channels", 4, 3)
        )
        val packed = HomePanelLayout.packedVisible(HomePanelLayout.EXPANDED, layout)
        assertEquals(listOf("overview", "apps", "drawer", "sync"), packed.take(4).map { it.id })
        assertEquals(HomePanelLayout.normalize(HomePanelLayout.EXPANDED, layout).count { it.visible }, packed.size)
        assertEquals(packed.map { it.id }.toSet().size, packed.size)
        assertTrue(packed.all { it.span in 1..4 })
    }

    @Test fun homePackingOmitsHiddenItemsWithoutChangingStoredOrder() {
        val start = HomePanelLayout.default(HomePanelLayout.COVER)
        val hidden = HomePanelLayout.visibility(HomePanelLayout.COVER, start, "sync", false)
        val before = hidden.map { it.id }
        val packed = HomePanelLayout.packedVisible(HomePanelLayout.COVER, hidden)
        assertFalse(packed.any { it.id == "sync" })
        assertEquals(before, hidden.map { it.id })
    }
}
