package net.automationse.relayharbor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardCodecTest {
    @Test fun roundTripAndClampMetrics() {
        val card = RichCard(
            "案件評価", "テスト", 92, "高適合",
            listOf(CardMetric("収益性", 150))
        )
        val decoded = CardCodec.decode(CardCodec.encode(card))
        assertEquals("案件評価", decoded?.title)
        assertEquals(92, decoded?.score)
        assertEquals(100, decoded?.metrics?.first()?.percent)
    }

    @Test fun rssArticleLinkIsValidated() {
        val card = RichCard(
            title = "RSS News", description = "New update",
            badge = "RSS", url = "https://github.blog/sample"
        )
        assertEquals(
            "https://github.blog/sample",
            CardCodec.decode(CardCodec.encode(card))?.url
        )
        assertNull(CardCodec.decode("""{"title":"Bad link","url":"javascript:alert(1)"}""")?.url)
    }

    @Test fun invalidPayloadIsIgnored() {
        assertNull(CardCodec.decode("{broken"))
        assertNull(CardCodec.decode("{}"))
    }
}
