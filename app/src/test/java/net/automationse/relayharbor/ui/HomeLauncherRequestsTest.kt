package net.automationse.relayharbor.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLauncherRequestsTest {
    @Test fun homeIntentIsConsumedOnce() {
        HomeLauncherRequests.consume()
        assertFalse(HomeLauncherRequests.requested.value)
        try {
            HomeLauncherRequests.openHome()
            assertTrue(HomeLauncherRequests.requested.value)
        } finally {
            HomeLauncherRequests.consume()
        }
        assertFalse(HomeLauncherRequests.requested.value)
    }
}
