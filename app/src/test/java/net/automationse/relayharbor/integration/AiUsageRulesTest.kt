package net.automationse.relayharbor.integration

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test

class AiUsageRulesTest {
    @Test fun rateBoundsAndPartialInputs() {
        assertEquals(0.0, AiUsageRules.rate("0")!!, 0.0)
        assertEquals(1.25, AiUsageRules.rate(" 1.25 ")!!, 0.0)
        assertNull(AiUsageRules.rate(""))
        assertNull(AiUsageRules.rate("unknown"))
        assertNull(AiUsageRules.rate("-1"))
        assertNull(AiUsageRules.rate("NaN"))
        assertNull(AiUsageRules.rate("Infinity"))
        assertNull(AiUsageRules.rate("1001"))
        assertTrue(AiUsageRules.validRates("0", "3.0"))
        assertFalse(AiUsageRules.validRates("1", ""))
        assertFalse(AiUsageRules.validRates("abc", "2"))
        assertEquals(listOf(1, 7, 30, 90, 365), AiUsageRules.periods)
    }
}
