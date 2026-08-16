package com.html_reader

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import core.cache.OrphanCacheCleaner
import java.io.File

class AppOrphanCacheCleanupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val daysUnused = inputData.getInt("daysUnused", AppMaintenancePolicy.ORPHAN_DAYS_UNUSED)
        return runCatching {
            val cacheRoot = File(applicationContext.cacheDir, "app_cache")
            val activeKeys = ReaderRuntime.tabCacheRegistry(applicationContext).activeCacheKeys()
            OrphanCacheCleaner(cacheRoot, daysUnused).clean(activeKeys)
            TransferCacheCleaner.clean(cacheDir = applicationContext.cacheDir, daysUnused = daysUnused)
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}
