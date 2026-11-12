package net.automationse.relayharbor.ui

import android.content.Context

/**
 * Device-private preferences for UX state. These are display choices only:
 * no server tokens, passwords, message bodies or application credentials.
 */
internal class WorkspaceUiStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "workspace_ui_v1", Context.MODE_PRIVATE
    )

    companion object {
        private val tabs = setOf("home", "channels", "activity", "integrations", "settings", "chat")
        private val channelFilters = setOf("all", "recent", "unread")
        private val activityFilters = setOf("all", "unread", "read")
        private val integrationFilters = setOf("all", "rss", "webhook", "active")

        fun safePage(value: String?): String =
            value?.takeIf { it in tabs } ?: "home"

        fun safeFilter(value: String?, allowed: Set<String>): String =
            value?.takeIf { it in allowed } ?: "all"

        fun channelFilter(value: String?): String = safeFilter(value, channelFilters)
        fun activityFilter(value: String?): String = safeFilter(value, activityFilters)
        fun integrationFilter(value: String?): String = safeFilter(value, integrationFilters)
        fun integrationCardMode(value: String?): String =
            value?.takeIf { it == "compact" } ?: "normal"

        fun safeForegroundSyncSeconds(value: Int): Int =
            value.takeIf { it in setOf(30, 60, 120, 300) } ?: 30
    }

    fun resumeLastPage(): Boolean = preferences.getBoolean("resume_last_page", true)
    fun setResumeLastPage(value: Boolean) {
        preferences.edit().putBoolean("resume_last_page", value).apply()
    }

    fun lastPage(): String = safePage(preferences.getString("last_page", "home"))
    fun savePage(value: String) {
        preferences.edit().putString("last_page", safePage(value)).apply()
    }

    fun previousPage(): String = safePage(
        preferences.getString("previous_page", "channels")
    ).takeUnless { it == "chat" } ?: "channels"

    fun savePreviousPage(value: String) {
        val safe = safePage(value).takeUnless { it == "chat" } ?: "channels"
        preferences.edit().putString("previous_page", safe).apply()
    }

    fun lastChatId(): Long = preferences.getLong("last_chat_id", 0L).coerceAtLeast(0L)
    fun saveLastChatId(value: Long) {
        preferences.edit().putLong("last_chat_id", value.coerceAtLeast(0L)).apply()
    }

    fun channelSearch(): String = preferences.getString("channel_search", "").orEmpty()
    fun saveChannelSearch(value: String) {
        preferences.edit().putString("channel_search", value.take(160)).apply()
    }

    fun channelListFilter(): String = channelFilter(
        preferences.getString("channel_filter", "all")
    )
    fun saveChannelListFilter(value: String) {
        preferences.edit().putString("channel_filter", channelFilter(value)).apply()
    }

    fun activitySearch(): String = preferences.getString("activity_search", "").orEmpty()
    fun saveActivitySearch(value: String) {
        preferences.edit().putString("activity_search", value.take(160)).apply()
    }

    fun activityListFilter(): String = activityFilter(
        preferences.getString("activity_filter", "all")
    )
    fun saveActivityListFilter(value: String) {
        preferences.edit().putString("activity_filter", activityFilter(value)).apply()
    }

    fun integrationSearch(): String = preferences.getString("integration_search", "").orEmpty()
    fun saveIntegrationSearch(value: String) {
        preferences.edit().putString("integration_search", value.take(160)).apply()
    }

    fun integrationListFilter(): String = integrationFilter(
        preferences.getString("integration_filter", "all")
    )
    fun saveIntegrationListFilter(value: String) {
        preferences.edit().putString("integration_filter", integrationFilter(value)).apply()
    }

    fun integrationCardLayout(): String = integrationCardMode(
        preferences.getString("integration_card_layout", "normal")
    )
    fun saveIntegrationCardLayout(value: String) {
        preferences.edit()
            .putString("integration_card_layout", integrationCardMode(value))
            .apply()
    }

    fun foregroundSyncSeconds(): Int = safeForegroundSyncSeconds(
        preferences.getInt("foreground_sync_seconds", 30)
    )
    fun saveForegroundSyncSeconds(value: Int) {
        preferences.edit()
            .putInt("foreground_sync_seconds", safeForegroundSyncSeconds(value))
            .apply()
    }

    fun showRemoteThumbnails(): Boolean =
        preferences.getBoolean("show_remote_thumbnails", true)

    fun saveShowRemoteThumbnails(value: Boolean) {
        preferences.edit().putBoolean("show_remote_thumbnails", value).apply()
    }

    fun showHomeActivity(): Boolean = preferences.getBoolean("show_home_activity", true)
    fun saveShowHomeActivity(value: Boolean) {
        preferences.edit().putBoolean("show_home_activity", value).apply()
    }

    fun showHomeChannels(): Boolean = preferences.getBoolean("show_home_channels", true)
    fun saveShowHomeChannels(value: Boolean) {
        preferences.edit().putBoolean("show_home_channels", value).apply()
    }
}
