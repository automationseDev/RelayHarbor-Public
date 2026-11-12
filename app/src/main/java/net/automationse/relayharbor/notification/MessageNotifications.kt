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

const val EXTRA_OPEN_CHANNEL_ID = "net.automationse.relayharbor.OPEN_CHANNEL_ID"

/** Shared only in this Android process; used to prevent noisy alerts for the open chat. */
object VisibleChat {
    @Volatile var appVisible: Boolean = false
    @Volatile var channelId: Long = 0L
}

object MessageNotifications {
    private fun channelKey(id: Long): String = "rh_channel_$id"

    fun hasPermission(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun ensureChannel(context: Context, id: Long, name: String): String {
        val key = channelKey(id)
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            key,
            name.take(60),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "RelayHarbor: $name"
            enableVibration(true)
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
            .setContentTitle(channelName)
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
