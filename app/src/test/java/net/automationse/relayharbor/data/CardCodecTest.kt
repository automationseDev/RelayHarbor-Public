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
    @Test fun thumbnailRoundTripSupportsCommonWebhookAliases() {
        val img = "https://cdn.example.com/photo.webp"
        val card = RichCard(title = "Thumbnail", thumbnailUrl = img)
        assertEquals(img, CardCodec.decode(CardCodec.encode(card))?.thumbnailUrl)
        for (field in listOf("thumbnail_url", "thumbnailUrl", "image_url", "imageUrl",
            "thumbnail", "image")) {
            val json = """{"title":"From Bot","$field":"$img"}"""
            assertEquals(img, CardCodec.decode(json)?.thumbnailUrl)
        }
        assertEquals(img, CardCodec.decode(
            """{"title":"Nested","image":{"url":"$img"}}"""
        )?.thumbnailUrl)
        assertEquals(img, CardCodec.decode(
            """{"title":"Gallery","images":[{"url":"$img"}]}"""
        )?.thumbnailUrl)
    }

    @Test fun thumbnailRejectsUnsafeTargetsWithoutBreakingCards() {
        val unsafe = listOf(
            "http://cdn.example.com/a.png",
            "file:///etc/passwd",
            "data:image/png;base64,aaaa",
            "https://localhost/private.png",
            "https://192.168.1.1/admin.jpg",
            "https://127.0.0.1/a.jpg",
            "https://nas.local/a.jpg",
            "https://user:password@cdn.example.com/a.jpg",
            "https://cdn.example.com:8080/p.jpg",
            "https://cdn.example.com/b.jpg#fragment",
            "javascript:alert(1)"
        )
        unsafe.forEach { url ->
            assertNull(PublicThumbnailUrl.validate(url))
            assertNull(CardCodec.decode("""{"title":"Test","thumbnail_url":"$url"}""")
                ?.thumbnailUrl)
        }
        assertNull(PublicThumbnailUrl.validate("https://cdn.example.com/" + "x".repeat(2100)))
        assertEquals("https://images.example.org/pic.jpg",
            PublicThumbnailUrl.validate("https://images.example.org/pic.jpg"))
    }

}
