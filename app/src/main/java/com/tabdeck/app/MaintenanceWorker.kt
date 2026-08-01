package com.tabdeck.app

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tabdeck.app.data.TabDeckRepository
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/** Runs idempotent local maintenance outside the activity lifecycle. */
class MaintenanceWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as? TabDeckApplication)?.repository
            ?: TabDeckRepository(applicationContext)
        return try {
            val settings = repository.currentState().settings
            if (!settings.automaticMaintenanceEnabled) return Result.success()
            repository.runMaintenance(settings.trashRetentionDays)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (problem: Exception) {
            runCatching { repository.recordMaintenanceFailure(problem.message.orEmpty()) }
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val UNIQUE_PERIODIC_WORK = "tabdeck-periodic-maintenance"
        private const val MAX_RETRY_ATTEMPTS = 2

        fun reconcileSchedule(context: Context, enabled: Boolean) {
            val manager = WorkManager.getInstance(context)
            if (!enabled) {
                manager.cancelUniqueWork(UNIQUE_PERIODIC_WORK)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(
                24,
                TimeUnit.HOURS,
                6,
                TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(UNIQUE_PERIODIC_WORK)
                .build()
            manager.enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
