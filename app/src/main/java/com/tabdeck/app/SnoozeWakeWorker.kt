package com.tabdeck.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Restores snoozed tabs even if TabDeck has not been opened since scheduling. */
class SnoozeWakeWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        val repository = (applicationContext as? TabDeckApplication)?.repository
            ?: com.tabdeck.app.data.TabDeckRepository(applicationContext)
        repository.wakeDueTabs()
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }

    companion object {
        fun schedule(context: Context, untilEpochMs: Long) {
            val delayMs = (untilEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
            val bucket = untilEpochMs / TimeUnit.MINUTES.toMillis(1)
            val request = OneTimeWorkRequestBuilder<SnoozeWakeWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "tabdeck-snooze-$bucket",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
