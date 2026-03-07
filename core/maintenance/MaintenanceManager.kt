package core.maintenance

/**
 * 统一维护入口接口：启动/后台清理可按需调度。
 */
interface MaintenanceManager {
    suspend fun runStartupMaintenance()
    suspend fun runBackgroundMaintenance()
}
