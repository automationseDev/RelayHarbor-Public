package net.automationse.relayharbor.notification

/**
 * Android notification channels keep user-modified settings. Separate channel keys
 * let users switch defaults without silently overwriting system preferences.
 */
object NotificationPriorityPolicy {
    const val HIGH = "high"
    const val NORMAL = "normal"
    const val SILENT = "silent"

    fun sanitize(value: String?): String = when (value) {
        HIGH -> HIGH
        SILENT -> SILENT
        else -> NORMAL
    }

    fun key(id: Long, priority: String): String =
        if (sanitize(priority) == NORMAL) "rh_channel_" + id
        else "rh_channel_" + id + "_" + sanitize(priority)

    fun label(priority: String): String = when (sanitize(priority)) {
        HIGH -> "高"
        SILENT -> "サイレント"
        else -> "通常"
    }
}
