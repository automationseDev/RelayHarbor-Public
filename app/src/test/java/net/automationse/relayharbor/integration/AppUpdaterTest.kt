package net.automationse.relayharbor.integration

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdaterTest {
    @Test fun semanticVersionComparison() {
        assertTrue(AppUpdater.isNewer("v0.5.1", "0.5.0"))
        assertTrue(AppUpdater.isNewer("0.6.0", "0.5.99"))
        assertTrue(AppUpdater.isNewer("1.0.0", "0.99.99"))
        assertFalse(AppUpdater.isNewer("v0.5.1", "0.5.1"))
        assertFalse(AppUpdater.isNewer("0.5.0", "0.5.1"))
        assertFalse(AppUpdater.isNewer("v0.5.1-beta", "0.5.0"))
        assertFalse(AppUpdater.isNewer("bad-version", "0.5.0"))
    }
}
