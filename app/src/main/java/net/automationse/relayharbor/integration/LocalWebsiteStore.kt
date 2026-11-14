package net.automationse.relayharbor.integration

import android.content.Context
import org.json.JSONObject
import java.net.URI
import java.util.UUID

/** Private per-device source settings; never requires a Relay server or stores credentials. */
data class LocalWebsiteWatch(
    val id: String,
    val name: String,
    val url: String,
    val channelId: Long,
    val intervalMinutes: Int,
    val selector: String = "",
    val keyword: String = "",
    val enabled: Boolean = true,
    val generation: Long = 1L,
    val revision: Long = 0L,
    val initialized: Boolean = false,
    val hash: String = "",
    val baselineText: String = "",
    val etag: String = "",
    val modified: String = "",
    val lastChecked: Long = 0,
    val lastSuccess: Long = 0,
    val lastError: String = ""
)

object LocalWebsiteRules {
    const val MAX_WATCHES = 25
    const val MAX_BYTES = 1_048_576
    const val MAX_TEXT = 16_000
    val intervals = listOf(15, 30, 60, 180, 360, 720, 1440)

    /** HTTPS public host only; DNS and every redirect are checked again at download time. */
    fun validUrl(value: String): Boolean = runCatching {
        if (value.length !in 12..2048 || value.any { it.code < 32 || it.code == 127 }) return false
        val uri = URI(value.trim())
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.rawUserInfo == null && uri.rawFragment == null &&
            (uri.port == -1 || uri.port == 443) &&
            host.contains('.') && !host.startsWith('.') && !host.contains("..") &&
            !host.all { it.isDigit() || it == '.' } &&
            host != "localhost" &&
            listOf(".local", ".localhost", ".internal", ".home.arpa", ".test", ".invalid")
                .none(host::endsWith)
    }.getOrDefault(false)

    fun validate(name: String, url: String, channelId: Long, interval: Int, selector: String, keyword: String) {
        require(name.trim().length in 1..60) { "連携名を入力してください（最大60文字）" }
        require(validUrl(url)) { "公開HTTPSサイトのURLを入力してください" }
        require(channelId > 0) { "通知先チャンネルを選択してください" }
        require(interval in intervals) { "取得間隔は15分以上から選択してください" }
        require(selector.length <= 200 && selector.none { it.code < 32 || it.code == 127 }) {
            "CSSセレクターは200文字以内で指定してください"
        }
        require(keyword.length <= 100 && keyword.none { it.code < 32 || it.code == 127 }) {
            "キーワードは100文字以内で指定してください"
        }
        if (selector.isNotBlank()) {
            try {
                org.jsoup.Jsoup.parse("<html><body></body></html>").select(selector.trim())
            } catch (_: Exception) {
                throw IllegalArgumentException("CSSセレクターの書式が正しくありません")
            }
        }
    }
}

class LocalWebsiteStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "local_website_watch_v1", Context.MODE_PRIVATE
    )
    private fun key(id: String) = "watch:$id"
    private val lock = LocalWebsiteStore::class.java

    fun list(): List<LocalWebsiteWatch> = synchronized(lock) {
        prefs.all.filterKeys { it.startsWith("watch:") }.values.mapNotNull {
            runCatching { decode(JSONObject(it as String)) }.getOrNull()
        }.sortedBy { it.name.lowercase() }
    }

    fun get(id: String): LocalWebsiteWatch? = synchronized(lock) {
        prefs.getString(key(id), null)?.let {
            runCatching { decode(JSONObject(it)) }.getOrNull()
        }
    }

    fun save(
        existingId: String?, name: String, url: String, channelId: Long,
        interval: Int, selector: String, keyword: String, enabled: Boolean = true
    ): LocalWebsiteWatch = synchronized(lock) {
        LocalWebsiteRules.validate(name, url, channelId, interval, selector, keyword)
        val previous = existingId?.let { get(it) }
        require(existingId == null || previous != null) { "監視設定が見つかりません" }
        if (previous == null) require(list().size < LocalWebsiteRules.MAX_WATCHES) {
            "端末内の監視は最大25件です"
        }
        val updatedSource = previous == null ||
            previous.url != url.trim() || previous.selector != selector.trim()
        val changed = LocalWebsiteWatch(
            id = previous?.id ?: UUID.randomUUID().toString(),
            name = name.trim(), url = url.trim(), channelId = channelId,
            intervalMinutes = interval, selector = selector.trim(),
            keyword = keyword.trim(), enabled = enabled,
            generation = (previous?.generation ?: 0) + 1,
            revision = if (updatedSource) 0 else (previous?.revision ?: 0),
            initialized = if (updatedSource) false else (previous?.initialized ?: false),
            hash = if (updatedSource) "" else previous?.hash.orEmpty(),
            baselineText = if (updatedSource) "" else previous?.baselineText.orEmpty(),
            etag = if (updatedSource) "" else previous?.etag.orEmpty(),
            modified = if (updatedSource) "" else previous?.modified.orEmpty(),
            lastChecked = if (updatedSource) 0 else (previous?.lastChecked ?: 0),
            lastSuccess = if (updatedSource) 0 else (previous?.lastSuccess ?: 0),
            lastError = ""
        )
        prefs.edit().putString(key(changed.id), encode(changed).toString()).commit()
        changed
    }

    fun remove(id: String) = synchronized(lock) {
        prefs.edit().remove(key(id)).commit()
    }

    /** Reject results from an old fetch if the source was edited/deleted in flight. */
    fun commitResult(id: String, generation: Long, next: LocalWebsiteWatch): Boolean =
        synchronized(lock) {
            val current = get(id) ?: return@synchronized false
            if (current.generation != generation) return@synchronized false
            prefs.edit().putString(key(id), encode(next).toString()).commit()
        }

    private fun encode(w: LocalWebsiteWatch): JSONObject = JSONObject()
        .put("id", w.id).put("name", w.name).put("url", w.url)
        .put("channelId", w.channelId).put("intervalMinutes", w.intervalMinutes)
        .put("selector", w.selector).put("keyword", w.keyword).put("enabled", w.enabled)
        .put("generation", w.generation).put("revision", w.revision)
        .put("initialized", w.initialized).put("hash", w.hash)
        .put("baselineText", w.baselineText).put("etag", w.etag)
        .put("modified", w.modified).put("lastChecked", w.lastChecked)
        .put("lastSuccess", w.lastSuccess).put("lastError", w.lastError)

    private fun decode(j: JSONObject) = LocalWebsiteWatch(
        id = j.getString("id"), name = j.getString("name"), url = j.getString("url"),
        channelId = j.getLong("channelId"), intervalMinutes = j.getInt("intervalMinutes"),
        selector = j.optString("selector"), keyword = j.optString("keyword"),
        enabled = j.optBoolean("enabled", true), generation = j.optLong("generation", 1),
        revision = j.optLong("revision"), initialized = j.optBoolean("initialized"),
        hash = j.optString("hash"), baselineText = j.optString("baselineText"),
        etag = j.optString("etag"), modified = j.optString("modified"),
        lastChecked = j.optLong("lastChecked"), lastSuccess = j.optLong("lastSuccess"),
        lastError = j.optString("lastError")
    )
}
