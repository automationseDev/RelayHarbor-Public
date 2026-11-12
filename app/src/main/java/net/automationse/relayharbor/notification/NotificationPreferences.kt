package net.automationse.relayharbor.notification

import android.content.Context

/**
 * On-device notification preferences. Does not contain Relay credentials or message bodies.
 * The Android system owns notification-channel sound and vibration choices.
 */
class NotificationPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "relayharbor_notifications_v1", Context.MODE_PRIVATE
    )

    fun enabled(): Boolean = prefs.getBoolean("enabled", true)
    fun setEnabled(value: Boolean) = prefs.edit().putBoolean("enabled", value).apply()

    fun backgroundEnabled(): Boolean = prefs.getBoolean("background", true)
    fun setBackgroundEnabled(value: Boolean) =
        prefs.edit().putBoolean("background", value).apply()

    fun requestedRuntimePermission(): Boolean = prefs.getBoolean("permission_prompted", false)
    fun markPermissionRequested() = prefs.edit().putBoolean("permission_prompted", true).apply()

    fun channelEnabled(id: Long): Boolean = prefs.getBoolean("channel_$id", true)
    fun setChannelEnabled(id: Long, enabled: Boolean) =
        prefs.edit().putBoolean("channel_$id", enabled).apply()
}

/** Ignore historical backfills and suspicious future timestamps. */
internal fun shouldNotifyTimestamp(
    timestamp: Long,
    now: Long,
    maxAgeMs: Long = 24L * 60L * 60L * 1000L
): Boolean = timestamp in (now - maxAgeMs)..(now + 5L * 60L * 1000L)
