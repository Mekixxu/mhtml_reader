package core.data.repo

import core.common.DispatcherProvider
import core.common.UrlCredentialSanitizer
import core.database.dao.HistoryDao
import core.database.entity.HistoryEntity
import core.database.entity.enums.FileType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class HistoryRepository(
    private val dao: HistoryDao,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend fun recordOpen(path: String, fileType: FileType, title: String) =
        withContext(dispatcherProvider.io) {
            // 原子 UPSERT，保留已有 progress/pageIndex，避免读改写竞态覆盖进度
            dao.touchOpen(
                path = path,
                title = title,
                lastAccess = System.currentTimeMillis(),
                fileType = fileType
            )
        }

    // DAO 自带调度，不加 withContext(io)，以便在 Room 事务中调用；写入口统一剥离 URL 密码
    suspend fun upsert(entity: HistoryEntity) =
        dao.upsert(entity.copy(path = UrlCredentialSanitizer.stripPassword(entity.path)))

    suspend fun updateProgress(path: String, progress: Float, pageIndex: Int) =
        withContext(dispatcherProvider.io) {
            dao.updateProgress(
                path = path,
                progress = progress,
                pageIndex = pageIndex,
                lastAccess = System.currentTimeMillis()
            )
        }

    fun observeRecent(limit: Int, offset: Int = 0): Flow<List<HistoryEntity>> = dao.observeRecent(limit, offset)

    suspend fun getRecent(limit: Int, offset: Int = 0): List<HistoryEntity> =
        withContext(dispatcherProvider.io) { dao.getRecent(limit, offset) }

    suspend fun getByPath(path: String): HistoryEntity? =
        withContext(dispatcherProvider.io) { dao.getByPath(path) }

    suspend fun clearAll() = dao.clearAll()

    suspend fun deleteOne(path: String) = withContext(dispatcherProvider.io) { dao.delete(path) }

    /**
     * 保留策略：先删过期，再按条数裁剪。
     * 注意：maxDays 可能为用户配置的较小值（如 30）。
     */
    suspend fun enforceRetention(maxItems: Int, maxDays: Int) = withContext(dispatcherProvider.io) {
        if (maxDays > 0) {
            val cutoff = System.currentTimeMillis() - maxDays * 86_400_000L
            dao.deleteOlderThan(cutoff)
        }
        if (maxItems > 0) {
            dao.deleteOldest(maxItems)
        }
    }

    suspend fun getAll(): List<HistoryEntity> = withContext(dispatcherProvider.io) { dao.getAll() }
}

