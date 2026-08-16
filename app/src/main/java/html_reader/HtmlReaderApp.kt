package com.html_reader

import android.app.Application
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class HtmlReaderApp : Application() {

    override fun onCreate() {
        super.onCreate()
        scheduleMaintenanceWorkers()
    }

    /**
     * 唯一生效的维护调度入口。
     *
     * 参数统一从 AppMaintenancePolicy 读取；启动时不再额外执行一次全量清理，
     * 避免与每日 Worker 形成双轨重复。core/work 目录中的 Hilt Worker 已编译
     * 但未接入运行时，后续迁移 Hilt 时应删除这里的注册逻辑。
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
}
