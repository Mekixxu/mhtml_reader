package core.work.scheduler

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * WorkManager调度实现。周期性维护与即刻维护均支持防重复。
 * 默认周期 24h；可按需修改。
 */
class DefaultWorkScheduler(
    private val context: Context
) : WorkScheduler {
    private val periodicWorkName = "maintenance_periodic"
    private val oneTimeWorkName = "maintenance_now"

    override fun schedulePeriodicMaintenance() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<core.work.worker.MaintenanceWorker>(
            24, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            periodicWorkName,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    override fun runMaintenanceNow() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        val request = OneTimeWorkRequestBuilder<core.work.worker.MaintenanceWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            oneTimeWorkName,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    override fun cancelMaintenance() {
        WorkManager.getInstance(context).cancelUniqueWork(periodicWorkName)
        WorkManager.getInstance(context).cancelUniqueWork(oneTimeWorkName)
    }
}
