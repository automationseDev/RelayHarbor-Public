package net.automationse.relayharbor.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import net.automationse.relayharbor.MainActivity
import net.automationse.relayharbor.R
import net.automationse.relayharbor.data.ImportedRelayMessage
import net.automationse.relayharbor.data.ChannelOrganizationStore

const val EXTRA_OPEN_CHANNEL_ID = "net.automationse.relayharbor.OPEN_CHANNEL_ID"

/** Shared only in this Android process; used to prevent noisy alerts for the open chat. */
object VisibleChat {
    @Volatile var appVisible: Boolean = false
    @Volatile var channelId: Long = 0L
}

object MessageNotifications {
    fun hasPermission(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun ensureChannel(context: Context, id: Long, name: String): String {
        val priority = NotificationPreferences(context).channelPriority(id)
        val key = NotificationPriorityPolicy.key(id, priority)
        val manager = context.getSystemService(NotificationManager::class.java)
        val importance = when (priority) {
            NotificationPriorityPolicy.HIGH -> NotificationManager.IMPORTANCE_HIGH
            NotificationPriorityPolicy.SILENT -> NotificationManager.IMPORTANCE_LOW
            else -> NotificationManager.IMPORTANCE_DEFAULT
        }
        val displayedName = ChannelOrganizationStore(context).displayName(id, name)
        val channel = NotificationChannel(
            key,
            displayedName.take(60) + if (priority == NotificationPriorityPolicy.NORMAL)
                "" else "（" + NotificationPriorityPolicy.label(priority) + "）",
            importance
        ).apply {
            description = "RelayHarbor: " + displayedName
            enableVibration(priority != NotificationPriorityPolicy.SILENT)
            if (priority == NotificationPriorityPolicy.SILENT) {
                setSound(null, null)
            }
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
        return key
    }

    fun openChannelSettings(context: Context, id: Long, name: String) {
        val key = ensureChannel(context, id, name)
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, key)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun showImported(context: Context, events: List<ImportedRelayMessage>) {
        if (events.isEmpty()) return
        val preferences = NotificationPreferences(context)
        if (!preferences.enabled() || !hasPermission(context)) return
        val now = System.currentTimeMillis()
        // Show at most 5 alerts per synchronization to avoid notification storms.
        events.asSequence()
            .filter { shouldNotifyTimestamp(it.timestamp, now) }
            .filter { preferences.channelEnabled(it.channelId) }
            .filterNot { VisibleChat.appVisible && VisibleChat.channelId == it.channelId }
            .toList()
            .takeLast(5)
            .forEach { event ->
                send(
                    context = context,
                    channelId = event.channelId,
                    channelName = event.channelName,
                    sender = event.sender,
                    text = event.text,
                    key = "${event.source}:${event.sequence}"
                )
            }
    }

    fun showAiReply(context: Context, id: Long, channelId: Long, channelName: String, text: String) {
        val prefs = NotificationPreferences(context)
        if (!prefs.enabled() || !prefs.channelEnabled(channelId) || !hasPermission(context)) return
        if (VisibleChat.appVisible && VisibleChat.channelId == channelId) return
        send(context, channelId, channelName, "AI", text, "ai-reply:$id")
    }

    private fun send(
        context: Context,
        channelId: Long,
        channelName: String,
        sender: String,
        text: String,
        key: String
    ) {
        val systemChannel = ensureChannel(context, channelId, channelName)
        val displayName = ChannelOrganizationStore(context).displayName(channelId, channelName)
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_CHANNEL_ID, channelId)
            action = "net.automationse.relayharbor.open." + key.hashCode()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context, key.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val summary = text.replace('\n', ' ').trim().take(180)
        val displayed = if (summary.isEmpty()) "新しいメッセージが届きました" else summary
        val notification = NotificationCompat.Builder(context, systemChannel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(displayName)
            .setContentText("$sender: $displayed")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$sender: $displayed"))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(key, 0, notification)
    }
}
