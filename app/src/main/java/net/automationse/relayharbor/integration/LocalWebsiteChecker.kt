package net.automationse.relayharbor.integration

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.automationse.relayharbor.data.HubDatabase
import net.automationse.relayharbor.data.HubRepository
import net.automationse.relayharbor.data.RichCard
import net.automationse.relayharbor.notification.MessageNotifications
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.security.MessageDigest
import kotlin.math.min

data class LocalWebsiteCheckResult(
    val changed: Boolean = false,
    val baselineSaved: Boolean = false,
    val skipped: Boolean = false,
    val detail: String = ""
)

/** Pure text normalizer and excerpt logic, tested without Android networking. */
object LocalWebsiteHtml {
    fun extract(bytes: ByteArray, selector: String): String {
        require(bytes.isNotEmpty() && bytes.size <= LocalWebsiteRules.MAX_BYTES) {
            "Webページのサイズが大きすぎます（1MBまで）"
        }
        val document = Jsoup.parse(ByteArrayInputStream(bytes), null, "")
        document.select("script,style,noscript,template,svg,iframe").remove()
        val roots = if (selector.isBlank()) {
            document.select("nav,footer,header,aside,form").remove()
            listOfNotNull(document.body())
        } else {
            try {
                document.select(selector).take(30).also {
                    require(it.isNotEmpty()) { "指定したCSSセレクターに一致する要素がありません" }
                }
            } catch (e: org.jsoup.select.Selector.SelectorParseException) {
                throw IllegalArgumentException("CSSセレクターの書式が正しくありません")
            }
        }
        val blocks = roots.flatMap { root ->
            val leaves = root.select("h1,h2,h3,h4,h5,h6,p,li")
            if (leaves.isEmpty()) listOf(root.text()) else leaves.map { it.text() }
        }
        return blocks.map { it.replace(Regex("\\s+"), " ").trim() }
            .filter(String::isNotEmpty).joinToString("\n")
            .take(LocalWebsiteRules.MAX_TEXT)
            .also { require(it.isNotBlank()) { "監視範囲に本文がありません" } }
    }

    fun excerpt(old: String, new: String): String {
        val existing = old.lines().toSet()
        val additions = new.lines().filter { it !in existing }
        if (additions.isNotEmpty()) return additions.take(8).joinToString("\n").take(800)
        // Fallback: a textual change inside the same line.
        val first = old.zip(new).indexOfFirst { it.first != it.second }
            .let { if (it == -1) min(old.length, new.length) else it }
        return new.substring((first - 60).coerceAtLeast(0),
            (first + 600).coerceAtMost(new.length)).take(800)
    }

    fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

internal data class WebsiteFetch(val body: ByteArray?, val etag: String, val modified: String)

internal object LocalWebsiteNetwork {
    private fun denyPrivateDns(host: String) {
        val addresses = InetAddress.getAllByName(host)
        require(addresses.isNotEmpty()) { "接続先のDNSが見つかりません" }
        for (ip in addresses) {
            val address = ip.address
            val cgnat = ip is Inet4Address &&
                (address[0].toInt() and 255) == 100 &&
                ((address[1].toInt() and 255) in 64..127)
            val benchmark = ip is Inet4Address &&
                (address[0].toInt() and 255) == 198 &&
                ((address[1].toInt() and 255) in 18..19)
            val isDocumentation = ip is Inet4Address &&
                (address[0].toInt() and 255) == 192 &&
                (address[1].toInt() and 255) == 0 &&
                (address[2].toInt() and 255) == 0
            require(!ip.isAnyLocalAddress && !ip.isLoopbackAddress &&
                !ip.isLinkLocalAddress && !ip.isSiteLocalAddress &&
                !ip.isMulticastAddress && !cgnat && !benchmark && !isDocumentation) {
                "社内LAN・ローカルIP等の監視には対応していません"
            }
        }
    }

    suspend fun fetch(source: LocalWebsiteWatch): WebsiteFetch = withContext(Dispatchers.IO) {
        var address = source.url
        for (redirect in 0..3) {
            require(LocalWebsiteRules.validUrl(address)) { "公開HTTPSのURLではありません" }
            val uri = URI(address)
            denyPrivateDns(uri.host)
            val connection = URL(address).openConnection() as HttpsURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 6000
                connection.readTimeout = 12000
                connection.setRequestProperty("User-Agent", "RelayHarbor-Android/0.9.6 (website watcher)")
                connection.setRequestProperty("Accept", "text/html, application/xhtml+xml")
                if (redirect == 0 && source.initialized) {
                    if (source.etag.isNotBlank())
                        connection.setRequestProperty("If-None-Match", source.etag)
                    if (source.modified.isNotBlank())
                        connection.setRequestProperty("If-Modified-Since", source.modified)
                }
                val code = connection.responseCode
                if (code in setOf(301, 302, 303, 307, 308)) {
                    require(redirect < 3) { "リダイレクト回数が多すぎます" }
                    val location = connection.getHeaderField("Location")
                        ?: error("リダイレクト先がありません")
                    address = uri.resolve(location).toString()
                    continue
                }
                if (code == 304) {
                    require(source.initialized) { "初回取得時に304応答を受信しました" }
                    return@withContext WebsiteFetch(null, source.etag, source.modified)
                }
                if (code !in 200..299) throw IOException("サイトの応答: HTTP $code")
                val contentType = connection.contentType.orEmpty().substringBefore(';').trim().lowercase()
                require(contentType.isBlank() || contentType in
                    setOf("text/html", "application/xhtml+xml")) {
                    "HTMLページではありません（$contentType）"
                }
                val bytes = ByteArrayOutputStream()
                connection.inputStream.use { stream ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        require(bytes.size() + count <= LocalWebsiteRules.MAX_BYTES) {
                            "HTMLページが1MBを超えています"
                        }
                        bytes.write(buffer, 0, count)
                    }
                }
                return@withContext WebsiteFetch(
                    bytes.toByteArray(),
                    connection.getHeaderField("ETag").orEmpty().take(256),
                    connection.getHeaderField("Last-Modified").orEmpty().take(128)
                )
            } finally {
                connection.disconnect()
            }
        }
        error("リダイレクトの処理に失敗しました")
    }
}

/**
 * A single checker serializes manual and WorkManager runs in-process.
 * Page contents never leave this device. Alerts are committed to Room before
 * Android notifications are displayed.
 */
object LocalWebsiteChecker {
    private val lock = Mutex()

    suspend fun check(context: Context, id: String, force: Boolean = false): LocalWebsiteCheckResult =
        lock.withLock {
            val app = context.applicationContext
            val store = LocalWebsiteStore(app)
            val watch = store.get(id)
                ?: return@withLock LocalWebsiteCheckResult(skipped = true, detail = "監視は削除済みです")
            if (!watch.enabled) return@withLock LocalWebsiteCheckResult(
                skipped = true, detail = "監視を一時停止しています"
            )
            val now = System.currentTimeMillis()
            if (!force && now - watch.lastChecked < watch.intervalMinutes * 60_000L) {
                return@withLock LocalWebsiteCheckResult(skipped = true, detail = "次回の確認時刻を待機中")
            }

            try {
                val response = LocalWebsiteNetwork.fetch(watch)
                val text = response.body?.let { LocalWebsiteHtml.extract(it, watch.selector) }
                val digest = text?.let(LocalWebsiteHtml::sha256) ?: watch.hash
                val didChange = watch.initialized && text != null && digest != watch.hash
                val currentlySaved = store.get(id)
                if (currentlySaved?.generation != watch.generation || !currentlySaved.enabled) {
                    return@withLock LocalWebsiteCheckResult(skipped = true, detail = "監視設定が変更されました")
                }

                val repository = HubRepository(HubDatabase.get(app).dao())
                var notified = false
                if (didChange) {
                    val excerpt = LocalWebsiteHtml.excerpt(watch.baselineText, text!!)
                    val passesFilter = watch.keyword.isBlank() ||
                        text.contains(watch.keyword, ignoreCase = true)
                    if (passesFilter) {
                        val card = RichCard(
                            title = watch.name + " が更新されました",
                            description = excerpt, badge = "WEB更新",
                            url = watch.url
                        )
                        val imported = repository.saveLocalWebsiteUpdate(
                            watch.id, watch.generation, watch.revision + 1L,
                            watch.channelId, "Web更新監視", excerpt, card
                        )
                        if (imported != null) {
                            MessageNotifications.showImported(app, listOf(imported))
                            notified = true
                        }
                    }
                }
                val next = watch.copy(
                    initialized = true,
                    hash = digest,
                    baselineText = text ?: watch.baselineText,
                    etag = response.etag,
                    modified = response.modified,
                    lastChecked = now,
                    lastSuccess = now,
                    lastError = "",
                    revision = if (didChange) watch.revision + 1L else watch.revision
                )
                if (!store.commitResult(id, watch.generation, next))
                    return@withLock LocalWebsiteCheckResult(skipped = true, detail = "監視設定が更新されました")
                LocalWebsiteCheckResult(
                    changed = notified, baselineSaved = !watch.initialized,
                    detail = when {
                        !watch.initialized -> "現在のページを監視基準として保存しました"
                        didChange && notified -> "更新を検知し、チャンネルへ通知しました"
                        didChange -> "変更を検知しました（キーワード条件により通知なし）"
                        else -> "変更はありません"
                    }
                )
            } catch (e: Exception) {
                store.commitResult(
                    id, watch.generation,
                    watch.copy(lastChecked = System.currentTimeMillis(),
                        lastError = (e.message ?: "確認できませんでした").take(180))
                )
                LocalWebsiteCheckResult(
                    detail = "取得エラー: " + (e.message ?: "通信エラー").take(160)
                )
            }
        }
}
