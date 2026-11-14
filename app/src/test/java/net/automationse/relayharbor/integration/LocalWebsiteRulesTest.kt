package net.automationse.relayharbor.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalWebsiteRulesTest {
    @Test fun publicHttpsOnlyAndNoCredentialsOrCustomPorts() {
        assertTrue(LocalWebsiteRules.validUrl("https://example.com/news"))
        assertTrue(LocalWebsiteRules.validUrl("https://example.co.jp:443/updates?section=1"))
        listOf(
            "http://example.com/news", "https://localhost/news", "https://nas.local/news",
            "https://192.168.1.1/news", "https://127.0.0.1/news",
            "https://someone:secret@example.org/updates",
            "https://example.org:8080/updates", "https://example.org/a#frag",
            "file:///tmp/report", "https://example.org/" + "a".repeat(2100)
        ).forEach { assertFalse("Rejected: " + it, LocalWebsiteRules.validUrl(it)) }
    }

    @Test fun minimumIntervalAndWatchParametersAreBounded() {
        assertEquals(15, LocalWebsiteRules.intervals.minOrNull())
        LocalWebsiteRules.validate(
            "Public page", "https://example.org/news", 1, 15, "main .news", "update"
        )
        for (minutes in listOf(0, 1, 5, 14)) {
            try {
                LocalWebsiteRules.validate(
                    "Test", "https://example.org", 1, minutes, "", ""
                )
                throw AssertionError("Should reject $minutes")
            } catch (_: IllegalArgumentException) {}
        }
        try {
            LocalWebsiteRules.validate("Test", "https://example.org", 0, 30, "", "")
            throw AssertionError("Missing channel should be rejected")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun scriptsNavigationAndStylingAreExcludedFromTextHash() {
        val old = """
            <html><body><header>Time 10:00</header><nav>Ads A</nav>
            <main id="news"><h1>News</h1><p>Version 1</p></main>
            <script>document.write('random')</script><footer>Time 10:00</footer>
            </body></html>
        """.trimIndent()
        val newChromeOnly = old.replace("Time 10:00", "Time 11:00")
            .replace("Ads A", "Ads B").replace("random", "different")
        val a = LocalWebsiteHtml.extract(old.toByteArray(), "")
        val b = LocalWebsiteHtml.extract(newChromeOnly.toByteArray(), "")
        assertEquals(a, b)
        assertEquals("News\nVersion 1",
            LocalWebsiteHtml.extract(old.toByteArray(), "#news"))
        assertFalse(a.contains("document.write"))
        assertFalse(a.contains("Ads"))
        assertEquals(LocalWebsiteHtml.sha256(a), LocalWebsiteHtml.sha256(b))
    }

    @Test fun websiteChangesProduceShortUsefulExcerpts() {
        val old = "<main><p>Release v1</p></main>"
        val updated = "<main><p>Release v2</p><p>New feature</p></main>"
        val before = LocalWebsiteHtml.extract(old.toByteArray(), "main")
        val after = LocalWebsiteHtml.extract(updated.toByteArray(), "main")
        assertNotEquals(LocalWebsiteHtml.sha256(before), LocalWebsiteHtml.sha256(after))
        assertTrue(LocalWebsiteHtml.excerpt(before, after).contains("New feature"))
    }

    @Test fun unmatchedOrInvalidSelectorsAreRejected() {
        try {
            LocalWebsiteHtml.extract("<p>Hello</p>".toByteArray(), "#missing")
            throw AssertionError("Expected missing target error")
        } catch (_: IllegalArgumentException) {}
        try {
            LocalWebsiteHtml.extract("<p>Hello</p>".toByteArray(), "[bad")
            throw AssertionError("Expected CSS syntax error")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun hugeResponseContentIsRejected() {
        try {
            LocalWebsiteHtml.extract(ByteArray(LocalWebsiteRules.MAX_BYTES + 1), "")
            throw AssertionError("Expected size error")
        } catch (_: IllegalArgumentException) {}
    }
}
