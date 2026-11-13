package net.automationse.relayharbor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCenterRulesTest {
    private fun message(
        id: Long, read: Boolean = false, pinned: Boolean = false,
        later: Boolean = false, done: Boolean = false, score: Int? = null
    ): MessageEntity = MessageEntity(
        id = id, channelId = 1L, sender = "Demo", text = "テスト通知 " + id,
        kind = if (score != null) "card" else "text",
        cardJson = score?.let { CardCodec.encode(RichCard("優先案件", score = it)) },
        isRead = read, isPinned = pinned, savedForLater = later, isDone = done,
        timestamp = id * 1000
    )

    @Test fun priorityCanComeFromScoreOrUserPin() {
        assertTrue(NotificationCenterRules.important(message(1, score = 85)))
        assertFalse(NotificationCenterRules.important(message(2, score = 84)))
        assertTrue(NotificationCenterRules.important(message(3, pinned = true)))
        assertFalse(NotificationCenterRules.important(message(4)))
    }

    @Test fun completedNotificationsRemainRetrievableButLeaveMainInbox() {
        val done = message(1, done = true, pinned = true)
        assertFalse(NotificationCenterRules.matches(done, "all"))
        assertFalse(NotificationCenterRules.matches(done, "important"))
        assertFalse(NotificationCenterRules.matches(done, "unread"))
        assertTrue(NotificationCenterRules.matches(done, "done"))
    }

    @Test fun laterReadAndUnreadFiltersAreIndependent() {
        val later = message(1, read = true, later = true)
        assertTrue(NotificationCenterRules.matches(later, "later"))
        assertTrue(NotificationCenterRules.matches(later, "read"))
        assertFalse(NotificationCenterRules.matches(later, "unread"))
        assertTrue(NotificationCenterRules.matches(message(2), "unread"))
        assertEquals("all", NotificationCenterRules.filterId("some-unknown-filter"))
    }

    @Test fun searchesIncludeTitleAndDescription() {
        val msg = message(10, score = 92).copy(
            cardJson = CardCodec.encode(RichCard("Python automation", "FastAPI & Kotlin", score = 92))
        )
        assertTrue(NotificationCenterRules.matches(msg, "all", "fastapi"))
        assertTrue(NotificationCenterRules.matches(msg, "all", "python"))
        assertTrue(NotificationCenterRules.matches(msg, "all", "channel", "Demo Channel"))
        assertFalse(NotificationCenterRules.matches(msg, "all", "javascript"))
    }

    @Test fun importantTabSortsPinnedFirstThenLatest() {
        val messages = listOf(message(1), message(3), message(2, pinned = true))
        assertEquals(listOf(2L, 3L, 1L),
            NotificationCenterRules.sort(messages, "important").map { it.id })
        assertEquals(listOf(3L, 2L, 1L),
            NotificationCenterRules.sort(messages, "all").map { it.id })
    }
}
