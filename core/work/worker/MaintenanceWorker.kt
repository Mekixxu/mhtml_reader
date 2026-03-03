package core.work.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.hilt.work.HiltWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import core.maintenance.MaintenanceManager
import core.settings.repo.SettingsRepository

/**
 * 后台维护任务Worker。通过 Hilt 注入 MaintenanceManager 与 SettingsRepository。
 */
@HiltWorker
class MaintenanceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val maintenanceManager: MaintenanceManager,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // MaintenanceManager.runBackgroundMaintenance里已自动按settings执行
        try {
            maintenanceManager.runBackgroundMaintenance()
            // 可在此补充日志上报（仅显示class名/数据量，不显示真实路径）
            return Result.success()
        } catch (e: Exception) {
            // 异常自动重试
            return Result.retry()
        }
    }
}
