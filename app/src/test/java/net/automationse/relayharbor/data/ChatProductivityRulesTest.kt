package net.automationse.relayharbor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatProductivityRulesTest {
    private val root = MessageEntity(id=1,channelId=7,sender="Bot",text="作業依頼",isRead=false)
    private val pinned = MessageEntity(id=2,channelId=7,sender="Bot",text="重要連絡",
        isPinned=true,savedForLater=true)
    private val reply = MessageEntity(id=3,channelId=7,sender="自分",text="schedule",
        fromMe=true,parentMessageId=1)

    @Test fun searchFindsMessageAndNestedReplyWithoutFlatteningThread() {
        val all = listOf(root,pinned,reply)
        assertEquals(listOf(root),ChatProductivityRules.visible(all,null,"schedule","all"))
        assertEquals(listOf(root,reply),ChatProductivityRules.visible(all,1,"none","pinned"))
        assertEquals(listOf(pinned),ChatProductivityRules.visible(all,null,"重要","all"))
        assertTrue(ChatProductivityRules.visible(all,null,"missing","all").isEmpty())
    }

    @Test fun filtersAndEmptyQueryAreStable() {
        val all = listOf(root,pinned,reply)
        assertEquals(listOf(root,pinned),ChatProductivityRules.visible(all,null,"","all"))
        assertEquals(listOf(root),ChatProductivityRules.visible(all,null,"","unread"))
        assertEquals(listOf(pinned),ChatProductivityRules.visible(all,null,"","pinned"))
        assertEquals(listOf(pinned),ChatProductivityRules.visible(all,null,"","later"))
    }

    @Test fun templatesAreBoundedAndNeverAutoSend() {
        val text=ChatProductivityRules.preparedPrompt("summary","長文")
        assertTrue(text.contains("要約"))
        assertTrue(text.endsWith("長文"))
        assertEquals(8000,ChatProductivityRules.preparedPrompt("tasks","x".repeat(10000)).length)
        assertEquals("original",ChatProductivityRules.preparedPrompt("unknown","original"))
    }
}
