package net.automationse.relayharbor.notification

import java.security.MessageDigest

/** The user must explicitly opt in to each package AND choose an existing channel. */
internal data class AndroidAppRoute(
    val packageName: String,
    val channelId: Long,
    val keyword: String = "",
    val enabled: Boolean = true
)

internal object AndroidNotificationRules {
    const val MAX_ROUTES = 80
    const val MAX_TEXT_LENGTH = 4000
    private val packagePattern = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")
    // Deliberately conservative: never persist content that looks like verification or 2FA.
    private val verificationTerms = Regex(
        "(?i)(one[ -]?time|\\botp\\b|\\b2fa\\b|two[ -]?factor|verification[ -]?code|" +
            "security[ -]?code|login[ -]?code|passcode|認証コード|認証番号|確認コード|" +
            "ワンタイム|二段階認証|ログインコード|セキュリティコード|暗証番号)"
    )
    private val digitsOnlyCode = Regex("^\\s*[0-9][0-9\\s-]{2,12}[0-9]\\s*$")

    fun validPackage(value: String): Boolean =
        value.length in 3..220 && packagePattern.matches(value)

    fun allowRoute(
        selfPackage: String,
        route: AndroidAppRoute?,
        sourcePackage: String,
        title: String,
        body: String
    ): Boolean {
        if (!validPackage(sourcePackage) || sourcePackage == selfPackage) return false
        if (route == null || !route.enabled || route.packageName != sourcePackage ||
            route.channelId <= 0L) return false
        val cleanTitle = title.trim()
        val cleanBody = body.trim()
        if (cleanBody.isEmpty() && cleanTitle.isEmpty()) return false
        if (isSensitive(cleanTitle, cleanBody)) return false
        val keyword = route.keyword.trim()
        return keyword.isBlank() ||
            cleanTitle.contains(keyword, ignoreCase = true) ||
            cleanBody.contains(keyword, ignoreCase = true)
    }

    fun isSensitive(title: String, body: String): Boolean {
        val combined = "$title\n$body"
        return verificationTerms.containsMatchIn(combined) ||
            digitsOnlyCode.matches(body)
    }

    /** Stable per-notification key; no notification contents or raw key in the key column. */
    fun source(packageName: String, notificationKey: String): String =
        "android-app:" + packageName + ":" + digest(notificationKey).take(20)

    /**
     * Updates with the same displayed content are ignored; a changed notification
     * produces a new event even if Android reused its key.
     */
    fun revision(title: String, body: String): Long {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest((title + "\u0000" + body).toByteArray(Charsets.UTF_8))
        var number = 0L
        for (i in 0..7) number = (number shl 8) or (bytes[i].toLong() and 255L)
        return number
    }

    private fun digest(raw: String): String = MessageDigest.getInstance("SHA-256")
        .digest(raw.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
