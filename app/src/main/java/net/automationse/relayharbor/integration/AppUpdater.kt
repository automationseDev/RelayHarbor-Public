package net.automationse.relayharbor.integration

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.HttpURLConnection
import java.net.URL

/** A signed APK published in RelayHarbor-Public's GitHub Releases. */
data class AppRelease(
    val version: String,
    val downloadUrl: String,
    val sha256: String,
    val size: Long,
    val pageUrl: String
)

object AppUpdater {
    private const val RELEASE_API =
        "https://api.github.com/repos/automationseDev/RelayHarbor-Public/releases/latest"
    private const val MAX_APK_BYTES = 150L * 1024 * 1024

    fun installedVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"

    private fun parseVersion(version: String): List<Int>? {
        val match = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""").matchEntire(version) ?: return null
        return match.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
    }

    fun isNewer(remote: String, local: String): Boolean {
        val a = parseVersion(remote) ?: return false
        val b = parseVersion(local) ?: return false
        return a.zip(b).firstOrNull { it.first != it.second }?.let {
            it.first > it.second
        } ?: false
    }

    private fun trustedUrl(value: String): URL {
        val url = URL(value)
        val host = url.host.lowercase()
        require(url.protocol.equals("https", ignoreCase = true) &&
            (host == "github.com" || host == "api.github.com" ||
                host == "githubusercontent.com" || host.endsWith(".githubusercontent.com"))) {
            "GitHub以外のダウンロード先が指定されました"
        }
        require(url.userInfo == null) { "認証情報付きのURLは使用できません" }
        return url
    }

    private fun connect(raw: String, accept: String): HttpURLConnection {
        var current = trustedUrl(raw)
        repeat(6) { hop ->
            val connection = current.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10000
            connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", "RelayHarbor-Android-Updater")
            connection.setRequestProperty("Accept", accept)
            val code = connection.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val location = connection.getHeaderField("Location")
                    ?: error("ダウンロード先が不明です")
                val next = current.toURI().resolve(location).toString()
                connection.disconnect()
                check(hop < 5) { "リダイレクトが多すぎます" }
                current = trustedUrl(next)
            } else {
                if (code !in 200..299) {
                    connection.disconnect()
                    error("GitHub HTTP $code：リリース情報を確認してください")
                }
                return connection
            }
        }
        error("ダウンロード先に接続できません")
    }

    suspend fun checkLatest(context: Context): AppRelease? = withContext(Dispatchers.IO) {
        val conn = connect(RELEASE_API, "application/vnd.github+json")
        val response = try {
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText().take(100_000) }
        } finally { conn.disconnect() }
        val obj = Json.parseToJsonElement(response).jsonObject
        val version = obj.getValue("tag_name").jsonPrimitive.content
        if (!isNewer(version, installedVersion(context))) return@withContext null
        val filename = "RelayHarbor-$version.apk"
        val asset = obj.getValue("assets").jsonArray
            .firstOrNull {
                it.jsonObject["name"]?.jsonPrimitive?.contentOrNull == filename
            }?.jsonObject ?: error("最新版の署名済みAPKが公開されていません")
        val url = asset.getValue("browser_download_url").jsonPrimitive.content
        trustedUrl(url)
        val digest = asset["digest"]?.jsonPrimitive?.contentOrNull
            ?: error("配信APKのSHA-256情報がありません")
        require(Regex("""sha256:[a-fA-F0-9]{64}""").matches(digest)) {
            "配信APKのSHA-256が不正です"
        }
        val size = asset["size"]?.jsonPrimitive?.longOrNull ?: 0
        require(size in 1..MAX_APK_BYTES) { "更新ファイルのサイズが不正です" }
        AppRelease(
            version = version,
            downloadUrl = url,
            sha256 = digest.substringAfter(":").lowercase(),
            size = size,
            pageUrl = obj.getValue("html_url").jsonPrimitive.content
        )
    }

    /**
     * Do not download or install APKs from inside the application.
     * Open the exact official GitHub release page in the system browser;
     * the user and Android decide whether to download or install.
     */
    fun openReleasePage(context: Context, release: AppRelease) {
        val url = trustedUrl(release.pageUrl)
        val expected = "https://github.com/automationseDev/RelayHarbor-Public/releases/tag/${release.version}"
        require(url.toExternalForm() == expected) {
            "公式のGitHub Releasesページ以外には移動できません"
        }
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(expected)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

}
