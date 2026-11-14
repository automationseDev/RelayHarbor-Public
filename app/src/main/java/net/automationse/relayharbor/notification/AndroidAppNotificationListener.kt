package net.automationse.relayharbor.notification

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.automationse.relayharbor.data.HubDatabase
import net.automationse.relayharbor.data.HubRepository

/**
 * Notification-only mirror. No SMS/LINE direct APIs, Accessibility service,
 * auto-replies, Android notification cancellation, or remote forwarding.
 *
 * Android binds this service only after the user grants Notification Access.
 * The app also requires a separately enabled global switch and a saved,
 * per-package, explicit channel rule before it writes anything.
 */
class AndroidAppNotificationListener : NotificationListenerService() {
    private val job = SupervisorJob()
    private val work = CoroutineScope(job + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        val store = AndroidAppNotificationStore(applicationContext)
        if (!store.globallyEnabled() || pkg == packageName) return
        val route = store.route(pkg) ?: return
        val notification = sbn.notification ?: return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 ||
            notification.flags and Notification.FLAG_ONGOING_EVENT != 0 ||
            notification.visibility == Notification.VISIBILITY_SECRET ||
            notification.category == Notification.CATEGORY_CALL ||
            notification.category == Notification.CATEGORY_SERVICE) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
            .orEmpty().take(200)
        val body = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            )?.toString()?.trim().orEmpty().take(AndroidNotificationRules.MAX_TEXT_LENGTH)
        if (!AndroidNotificationRules.allowRoute(packageName, route, pkg, title, body)) return

        val label = runCatching {
            val info = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrElse { pkg }.take(80)
        val notificationKey = sbn.key ?: return
        val postedAt = sbn.postTime.coerceAtLeast(1L)

        work.launch {
            // A rule or the global switch may have been disabled since the callback.
            val latest = AndroidAppNotificationStore(applicationContext)
            val effective = latest.route(pkg)
            if (!latest.globallyEnabled() ||
                !AndroidNotificationRules.allowRoute(
                    packageName, effective, pkg, title, body
                ) || effective == null) return@launch
            runCatching {
                HubRepository(HubDatabase.get(applicationContext).dao())
                    .importAndroidNotification(
                        sourcePackage = pkg,
                        notificationKey = notificationKey,
                        appName = label,
                        title = title,
                        body = body,
                        channelId = effective.channelId,
                        timestamp = postedAt
                    )
            }
            // No new Android OS notification is posted: the original app already
            // delivered one, and re-posting would create duplicate alerts.
        }
    }

    override fun onDestroy() {
        work.cancel()
        super.onDestroy()
    }
}
