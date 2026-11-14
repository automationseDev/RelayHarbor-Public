package net.automationse.relayharbor.floating

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingMenuPolicyTest {
    @Test fun safeSideDefaultsToRight() {
        assertEquals("left", FloatingMenuPolicy.side("left"))
        assertEquals("right", FloatingMenuPolicy.side("right"))
        assertEquals("right", FloatingMenuPolicy.side("invalid"))
        assertEquals("right", FloatingMenuPolicy.side(null))
    }

    @Test fun dragAndOpacityAreClampedToReachableVisibleAreas() {
        assertEquals(0.12f, FloatingMenuPolicy.fraction(-10f))
        assertEquals(0.82f, FloatingMenuPolicy.fraction(4f))
        assertEquals(0.38f, FloatingMenuPolicy.fraction(Float.NaN))
        assertEquals(0.4f, FloatingMenuPolicy.opacity(-1f))
        assertEquals(1.0f, FloatingMenuPolicy.opacity(3f))
        assertEquals(0.85f, FloatingMenuPolicy.opacity(Float.POSITIVE_INFINITY))
        assertEquals(32, FloatingMenuPolicy.handleDp(-9))
        assertEquals(64, FloatingMenuPolicy.handleDp(1000))
    }

    @Test fun onlyKnownTabsCanBeLaunchedFromFloatingOverlay() {
        listOf("home", "channels", "activity", "settings").forEach {
            assertEquals(it, FloatingMenuPolicy.validTab(it))
        }
        listOf("chat", "integrations", "camera", "intent://malicious", "", "SETTINGS")
            .forEach { assertNull(FloatingMenuPolicy.validTab(it)) }
    }

    @Test fun menuIsDisabledByDefaultAndOverlayRequiredBeforeStarting() {
        assertFalse(FloatingMenuOptions().enabled)
        assertEquals("right", FloatingMenuOptions().side)
        assertTrue(FloatingMenuOptions().opacity >= 0.4f)
    }
}
