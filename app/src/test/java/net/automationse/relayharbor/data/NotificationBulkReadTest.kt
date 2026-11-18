package net.automationse.relayharbor.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationBulkReadTest {
    @Test fun bulkTargetsOnlyUnreadIncomingVisibleNotifications() {
        val inbox = listOf(
            MessageEntity(id=11,channelId=1,sender="Bot",isRead=false),
            MessageEntity(id=12,channelId=1,sender="Bot",isRead=true),
            MessageEntity(id=13,channelId=1,sender="自分",fromMe=true,isRead=false),
            MessageEntity(id=11,channelId=1,sender="Bot",isRead=false),
            MessageEntity(id=0,channelId=1,sender="Bot",isRead=false)
        )
        assertEquals(listOf(11L), NotificationCenterRules.visibleUnreadIds(inbox))
    }
}
