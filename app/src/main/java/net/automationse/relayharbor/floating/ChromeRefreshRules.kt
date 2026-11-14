package net.automationse.relayharbor.floating

/**
 * Conservative matcher for Chrome *native* toolbar/menu controls.
 * Never select a text element from a webpage just because it says "Reload".
 */
internal object ChromeRefreshRules {
    const val CHROME_PACKAGE = "com.android.chrome"
    private const val RESOURCE_PREFIX = "com.android.chrome:id/"

    private val reloadLabels = setOf(
        "再読み込み",
        "ページを再読み込み",
        "このページを再読み込み",
        "Reload",
        "Reload this page",
        "Refresh",
        "Refresh this page"
    )
    private val menuLabels = setOf(
        "その他のオプション",
        "その他のメニュー",
        "More options",
        "More options menu"
    )

    fun isChrome(packageName: String?): Boolean = packageName == CHROME_PACKAGE

    /** Only native Chrome view IDs, never WebContents virtual accessibility nodes. */
    fun isReloadButton(resourceId: String?, description: String?, text: String?): Boolean {
        if (resourceId?.startsWith(RESOURCE_PREFIX) != true) return false
        val label = (description ?: text).orEmpty().trim()
        val id = resourceId.removePrefix(RESOURCE_PREFIX).lowercase()
        return label in reloadLabels && !label.startsWith("Stop", ignoreCase = true) &&
            !label.contains("停止") &&
            // Chrome menu refresh view and toolbar reload view are both allowed.
            (id.contains("menu") || id.contains("refresh") || id.contains("reload"))
    }

    fun isOverflowButton(resourceId: String?, description: String?, text: String?): Boolean {
        if (resourceId?.startsWith(RESOURCE_PREFIX) != true) return false
        val id = resourceId.removePrefix(RESOURCE_PREFIX).lowercase()
        val label = (description ?: text).orEmpty().trim()
        return id.contains("menu") && label in menuLabels
    }
}
