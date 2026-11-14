package net.automationse.relayharbor.ui

/** The title and quick action are part of each Home panel's non-scrolling chrome. */
internal data class HomePanelHeaderSpec(
    val title: String,
    val actionLabel: String? = null
)

internal object HomePanelHeaders {
    fun spec(id: String): HomePanelHeaderSpec = when (id) {
        "overview" -> HomePanelHeaderSpec("マイワークスペース")
        "apps" -> HomePanelHeaderSpec("お気に入りアプリ", "追加・編集")
        "drawer" -> HomePanelHeaderSpec("すべてのアプリ")
        "sync" -> HomePanelHeaderSpec("同期・通知")
        "activity" -> HomePanelHeaderSpec("最近のアクティビティ", "すべて見る")
        "channels" -> HomePanelHeaderSpec("チャンネル", "一覧へ")
        "clock" -> HomePanelHeaderSpec("時計")
        "date" -> HomePanelHeaderSpec("今日の日付")
        "weather" -> HomePanelHeaderSpec("天気")
        "agenda" -> HomePanelHeaderSpec("次の予定")
        "battery" -> HomePanelHeaderSpec("バッテリー")
        else -> HomePanelHeaderSpec("ホーム")
    }
}

/**
 * Six navigation destinations need compact labels on narrow Fold cover screens.
 * All destinations stay visible, icons have accessible content descriptions.
 */
internal object BottomNavigationPolicy {
    fun showAllLabels(availableWidthDp: Int, fontScale: Float): Boolean =
        availableWidthDp >= 540 && fontScale < 1.3f

    fun unreadBadge(count: Int): String =
        when {
            count <= 0 -> ""
            count > 99 -> "99+"
            else -> count.toString()
        }
}
