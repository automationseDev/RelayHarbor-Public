package net.automationse.relayharbor.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPriorityPolicyTest {
    @Test fun defaultsToLegacyNormalChannelForUpgradeCompatibility() {
        assertEquals(NotificationPriorityPolicy.NORMAL, NotificationPriorityPolicy.sanitize(null))
        assertEquals(NotificationPriorityPolicy.NORMAL, NotificationPriorityPolicy.sanitize("unknown"))
        assertEquals("rh_channel_11", NotificationPriorityPolicy.key(11, "normal"))
        assertEquals("rh_channel_11", NotificationPriorityPolicy.key(11, "unknown"))
    }

    @Test fun highAndSilentUseDistinctImmutableAndroidChannelIds() {
        assertEquals("rh_channel_11_high", NotificationPriorityPolicy.key(11, "high"))
        assertEquals("rh_channel_11_silent", NotificationPriorityPolicy.key(11, "silent"))
        assertEquals("高", NotificationPriorityPolicy.label("high"))
        assertEquals("通常", NotificationPriorityPolicy.label("normal"))
        assertEquals("サイレント", NotificationPriorityPolicy.label("silent"))
    }
}
