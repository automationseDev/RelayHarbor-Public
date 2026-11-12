package net.automationse.relayharbor.notification

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * A battery-conscious fallback when a user has closed the application.
 * WorkManager can delay this work under Doze/battery restrictions. It is not push.
 */
class RelayNotificationWorker(
    context: Context, params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = NotificationPreferences(applicationContext)
        if (!prefs.enabled() || !prefs.backgroundEnabled() ||
            !MessageNotifications.hasPermission(applicationContext)) {
            return Result.success()
        }
        return try {
            RelayEventSync.perform(applicationContext)
            Result.success()
        } catch (_: IllegalArgumentException) {
            // Not configured yet: do not turn a periodic job into a retry storm.
            Result.success()
        } catch (_: IllegalStateException) {
            // Missing/invalid credentials need the user to fix configuration.
            Result.success()
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

object NotificationSyncScheduler {
    private const val WORK_NAME = "relayharbor-periodic-inbound-sync-v1"

    fun refresh(context: Context) {
        val app = context.applicationContext
        val prefs = NotificationPreferences(app)
        val manager = WorkManager.getInstance(app)
        if (!prefs.enabled() || !prefs.backgroundEnabled() ||
            !MessageNotifications.hasPermission(app)) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<RelayNotificationWorker>(
            15, TimeUnit.MINUTES
        ).setConstraints(constraints).build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
