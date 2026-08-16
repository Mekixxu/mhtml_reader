package com.html_reader

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class AppHistoryRetentionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val maxItems = inputData.getInt("maxItems", AppMaintenancePolicy.HISTORY_MAX_ITEMS)
        val maxDays = inputData.getInt("maxDays", AppMaintenancePolicy.HISTORY_MAX_DAYS)
        return runCatching {
            ReaderRuntime.historyRepository(applicationContext).enforceRetention(maxItems, maxDays)
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}
