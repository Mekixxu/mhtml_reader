package core.work.scheduler

/**
 * 后台任务调度接口
 */
interface WorkScheduler {
    fun schedulePeriodicMaintenance()
    fun runMaintenanceNow()
    fun cancelMaintenance()
}
