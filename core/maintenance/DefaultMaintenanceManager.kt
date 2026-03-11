package core.maintenance

import core.settings.repo.SettingsRepository
import core.cache.OrphanCacheCleaner
import core.cache.CacheEvictor
import core.data.repo.HistoryRepository
import core.data.repo.TitleCacheRepository
import core.common.DispatcherProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 启动/后台维护管理器。所有IO都在dispatcherProvider.io。
 * - orphan cache 清理
 * - cache定期LRU淘汰（maxBytes）
 * - history裁剪
 * - title_cache清理
 */
class DefaultMaintenanceManager(
    private val settingsRepo: SettingsRepository,
    private val orphanCleaner: OrphanCacheCleaner,
    private val cacheEvictor: CacheEvictor,
    private val historyRepo: HistoryRepository,
    private val titleCacheRepo: TitleCacheRepository,
    private val dispatcherProvider: DispatcherProvider
) : MaintenanceManager {

    override suspend fun runStartupMaintenance() = withContext(dispatcherProvider.io) {
        val settings = settingsRepo.observe().first()
        orphanCleaner.clean()
        cacheEvictor.evictOldFiles(7L * 24 * 3600 * 1000) // 7 days retention
        cacheEvictor.evictIfNeeded() // Use configured maxBytes (15GB)
        historyRepo.enforceRetention(settings.historyMaxItems, settings.historyMaxDays)
        val cutoff = System.currentTimeMillis() - settings.titleCacheMaxDays * 86400_000L
        titleCacheRepo.deleteOlderThan(cutoff)
    }

    override suspend fun runBackgroundMaintenance() = runStartupMaintenance() // 可复用
}
