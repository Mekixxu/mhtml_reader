package com.html_reader

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException

class AppHistoryRetentionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val maxItems = inputData.getInt("maxItems", AppMaintenancePolicy.HISTORY_MAX_ITEMS)
        val maxDays = inputData.getInt("maxDays", AppMaintenancePolicy.HISTORY_MAX_DAYS)
        return try {
            ReaderRuntime.historyRepository(applicationContext).enforceRetention(maxItems, maxDays)
            Result.success()
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
