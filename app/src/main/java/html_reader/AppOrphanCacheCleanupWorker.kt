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
            // 标题缓存此前无任何生效清理入口，这里按策略统一裁剪
            val titleCutoff = System.currentTimeMillis() -
                AppMaintenancePolicy.TITLE_CACHE_MAX_DAYS * 86_400_000L
            FilesRuntime.titleCacheRepository(applicationContext).deleteOlderThan(titleCutoff)
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}
