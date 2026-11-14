package net.automationse.relayharbor.integration

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Android's battery-aware 15-minute minimum periodic job (not an exact timer). */
class LocalWebsiteWorker(
    context: Context, parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_WATCH_ID) ?: return Result.success()
        // Network errors are stored for the user; avoid aggressive retry loops.
        LocalWebsiteChecker.check(applicationContext, id)
        return Result.success()
    }

    companion object {
        const val KEY_WATCH_ID = "watch_id"
    }
}

object LocalWebsiteScheduler {
    private fun name(id: String) = "relayharbor-local-website-v1-$id"

    fun sync(context: Context) {
        val app = context.applicationContext
        LocalWebsiteStore(app).list().forEach { watch ->
            schedule(app, watch, replace = false)
        }
    }

    fun schedule(context: Context, watch: LocalWebsiteWatch, replace: Boolean = true) {
        val app = context.applicationContext
        val manager = WorkManager.getInstance(app)
        if (!watch.enabled) {
            manager.cancelUniqueWork(name(watch.id))
            return
        }
        val network = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<LocalWebsiteWorker>(
            watch.intervalMinutes.toLong(), TimeUnit.MINUTES
        ).setInputData(
            Data.Builder().putString(LocalWebsiteWorker.KEY_WATCH_ID, watch.id).build()
        ).setConstraints(network).build()
        manager.enqueueUniquePeriodicWork(
            name(watch.id),
            if (replace) ExistingPeriodicWorkPolicy.UPDATE
            else ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(context: Context, id: String) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(name(id))
    }
}
