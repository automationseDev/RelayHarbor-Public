package net.automationse.relayharbor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatConversationRulesTest {
    @Test fun threadRepliesStayOutOfChannelTimeline() {
        val root = MessageEntity(id = 1, channelId = 4, sender = "Bot", text = "start")
        val reply = MessageEntity(id = 2, channelId = 4, sender = "自分",
            text = "response", fromMe = true, parentMessageId = 1)
        assertEquals(listOf(root), ChatConversationRules.roots(listOf(root, reply)))
        assertEquals(listOf(reply), ChatConversationRules.thread(listOf(root, reply), 1))
    }

    @Test fun reactionsAreRestrictedAndLocalEditsAreOwnerOnly() {
        assertTrue(ChatConversationRules.validReaction("👍"))
        assertFalse(ChatConversationRules.validReaction("not-an-emoji"))
        assertTrue(ChatConversationRules.canEdit(
            MessageEntity(channelId = 1, sender = "自分", fromMe = true)))
        assertFalse(ChatConversationRules.canEdit(
            MessageEntity(channelId = 1, sender = "Bot", fromMe = false)))
        assertEquals("hello", ChatConversationRules.cleanBody("  hello  "))
        assertEquals(8000, ChatConversationRules.cleanBody("x".repeat(9000)).length)
    }
}
