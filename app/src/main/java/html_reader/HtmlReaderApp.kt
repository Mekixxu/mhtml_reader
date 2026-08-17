package com.html_reader

import android.app.Application
import android.util.Log
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
    private companion object {
        const val TAG = "HtmlReaderApp"
    }

    override fun onCreate() {
        super.onCreate()
        scheduleMaintenanceWorkers()
        migrateLegacyCredentials()
        cleanupOrphanCacheAtStartup()
    }

    /**
     * 唯一生效的维护调度入口。
     *
     * 参数统一从 AppMaintenancePolicy 读取。周期任务首次运行可能滞后一个周期；
     * 启动时再做一次轻量清理兜底（幂等，与周期 Worker 参数同源）。
     * core/work 目录中的 Hilt Worker 已编译但未接入运行时，后续迁移 Hilt 时应删除这里的注册逻辑。
     */
    private fun scheduleMaintenanceWorkers() {
        val workManager = WorkManager.getInstance(this)

        val historyInput = Data.Builder()
            .putInt("maxItems", AppMaintenancePolicy.HISTORY_MAX_ITEMS)
            .putInt("maxDays", AppMaintenancePolicy.HISTORY_MAX_DAYS)
            .build()
        val historyRequest = PeriodicWorkRequestBuilder<AppHistoryRetentionWorker>(1, TimeUnit.DAYS)
            .setInputData(historyInput)
            .build()
        workManager.enqueueUniquePeriodicWork(
            AppMaintenancePolicy.HISTORY_RETENTION_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            historyRequest
        )

        val orphanInput = Data.Builder()
            .putInt("daysUnused", AppMaintenancePolicy.ORPHAN_DAYS_UNUSED)
            .build()
        val orphanRequest = PeriodicWorkRequestBuilder<AppOrphanCacheCleanupWorker>(1, TimeUnit.DAYS)
            .setInputData(orphanInput)
            .build()
        workManager.enqueueUniquePeriodicWork(
            AppMaintenancePolicy.ORPHAN_CACHE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            orphanRequest
        )
    }

    /**
     * 一次性升级：把 v1.0 遗留的明文网络密码重加密写回（幂等，前缀判定）。
     */
    private fun migrateLegacyCredentials() {
        appScope.launch {
            runCatching {
                FilesRuntime.networkConfigRepository(applicationContext).migrateLegacyPlaintextIfNeeded()
            }.onFailure { e ->
                Log.w(TAG, "credential_migration_failed err=${e.javaClass.simpleName}")
            }
        }
    }

    /**
     * 启动清理：孤儿缓存 + ftp/smb 传输中转文件。
     * 防止周期 Worker 首跑延迟导致的分区占用；仅执行一次、幂等。
     */
    private fun cleanupOrphanCacheAtStartup() {
        appScope.launch {
            runCatching {
                val daysUnused = AppMaintenancePolicy.ORPHAN_DAYS_UNUSED
                val cacheRoot = File(cacheDir, "app_cache")
                val activeKeys = ReaderRuntime.tabCacheRegistry(applicationContext).activeCacheKeys()
                OrphanCacheCleaner(cacheRoot = cacheRoot, daysUnused = daysUnused).clean(activeKeys)
                TransferCacheCleaner.clean(cacheDir = cacheDir, daysUnused = daysUnused)
            }.onFailure { e ->
                Log.w(TAG, "startup_cache_cleanup_failed err=${e.javaClass.simpleName}")
            }
        }
    }
}