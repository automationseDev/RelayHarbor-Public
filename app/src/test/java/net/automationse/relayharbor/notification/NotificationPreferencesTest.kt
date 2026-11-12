package net.automationse.relayharbor.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPreferencesTest {
    @Test fun excludesOldBackfilledEvents() {
        val now = 1_700_000_000_000L
        assertFalse(shouldNotifyTimestamp(now - 25L * 60 * 60 * 1000, now))
        assertTrue(shouldNotifyTimestamp(now - 10L * 60 * 1000, now))
        assertTrue(shouldNotifyTimestamp(now, now))
    }

    @Test fun rejectsClockSkewTooFarIntoFuture() {
        val now = 1_700_000_000_000L
        assertTrue(shouldNotifyTimestamp(now + 5L * 60 * 1000, now))
        assertFalse(shouldNotifyTimestamp(now + 6L * 60 * 1000, now))
    }

    @Test fun supportsCustomBackfillCutoff() {
        assertTrue(shouldNotifyTimestamp(10L, 20L, 15L))
        assertFalse(shouldNotifyTimestamp(1L, 20L, 15L))
    }
}
