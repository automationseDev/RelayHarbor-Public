package net.automationse.relayharbor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelOrganizationRulesTest {
    @Test fun aliasesNeverContainLineBreaksOrNulCharacters() {
        assertEquals("Bot通知", ChannelOrganizationRules.cleanLabel("  Bot\n通知  "))
        assertEquals("💬", ChannelOrganizationRules.cleanEmoji(" 💬\n"))
        assertEquals(60, ChannelOrganizationRules.cleanLabel("a".repeat(100)).length)
    }

    @Test fun foldersHaveBoundedNamesAndCannotBeBlank() {
        assertEquals("仕事・AI", ChannelOrganizationRules.cleanFolder("  仕事・AI  "))
        assertTrue(ChannelOrganizationRules.validFolder("仕事"))
        assertFalse(ChannelOrganizationRules.validFolder(""))
        assertFalse(ChannelOrganizationRules.validFolder(" abc "))
        assertFalse(ChannelOrganizationRules.validFolder("x".repeat(33)))
    }

    @Test fun renameToSameRoutedChannelIsOnlyAnAlias() {
        val original = ChannelEntity(id = 44L, name = "github-actions", emoji = "🧩")
        val presented = original.copy(name = "CI監視", emoji = "🔔")
        assertEquals("github-actions", original.name)
        assertEquals("CI監視", presented.name)
        assertEquals(original.id, presented.id)
    }
}
