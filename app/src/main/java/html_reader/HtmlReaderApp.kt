package com.html_reader

import android.app.Application
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import core.cache.OrphanCacheCleaner
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class HtmlReaderApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        scheduleMaintenanceWorkers()
        cleanupOrphanCache()
    }

    private fun scheduleMaintenanceWorkers() {
        appScope.launch {
            val workManager = WorkManager.getInstance(this@HtmlReaderApp)

            val historyInput = Data.Builder()
                .putInt("maxItems", 500)
                .putInt("maxDays", 365)
                .build()
            val historyRequest = PeriodicWorkRequestBuilder<AppHistoryRetentionWorker>(1, TimeUnit.DAYS)
                .setInputData(historyInput)
                .build()
            workManager.enqueueUniquePeriodicWork(
                "history_retention_daily",
                ExistingPeriodicWorkPolicy.UPDATE,
                historyRequest
            )

            val orphanInput = Data.Builder()
                .putInt("daysUnused", 3)
                .build()
            val orphanRequest = PeriodicWorkRequestBuilder<AppOrphanCacheCleanupWorker>(1, TimeUnit.DAYS)
                .setInputData(orphanInput)
                .build()
            workManager.enqueueUniquePeriodicWork(
                "orphan_cache_cleanup_daily",
                ExistingPeriodicWorkPolicy.UPDATE,
                orphanRequest
            )
        }
    }

    private fun cleanupOrphanCache() {
        appScope.launch {
            val cacheRoot = File(cacheDir, "app_cache")
            OrphanCacheCleaner(cacheRoot = cacheRoot, daysUnused = 3).clean()
        }
    }
}
