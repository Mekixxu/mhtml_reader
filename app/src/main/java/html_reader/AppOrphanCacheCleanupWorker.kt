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
        val daysUnused = inputData.getInt("daysUnused", 3)
        return runCatching {
            val cacheRoot = File(applicationContext.cacheDir, "app_cache")
            OrphanCacheCleaner(cacheRoot, daysUnused).clean()
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}
