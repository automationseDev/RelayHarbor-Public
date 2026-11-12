package net.automationse.relayharbor.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickAppsSelectionTest {
    @Test fun addsAndRemovesWithoutReorderingOtherApps() {
        val first = toggleFavoriteApp(emptyList(), "com.example.one/.Main")
        val second = toggleFavoriteApp(first, "com.example.two/.Start")
        assertEquals(listOf("com.example.one/.Main", "com.example.two/.Start"), second)
        assertEquals(listOf("com.example.two/.Start"),
            toggleFavoriteApp(second, "com.example.one/.Main"))
    }

    @Test fun enforcesLimitWithoutMutatingExistingSelection() {
        val thirtyTwo = (1..32).map { "app$it" }
        assertEquals(thirtyTwo, toggleFavoriteApp(thirtyTwo, "app33"))
        assertEquals(31, toggleFavoriteApp(thirtyTwo, "app1").size)
    }
}
