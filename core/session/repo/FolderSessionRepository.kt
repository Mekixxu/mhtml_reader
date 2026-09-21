package core.session.repo

import core.session.dao.FolderSessionDao
import core.session.entity.FolderSessionEntity
import core.common.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 会话持久化操作，所有 DB 在 IO 线程。
 */
class FolderSessionRepository(
    private val dao: FolderSessionDao,
    private val dispatcherProvider: DispatcherProvider
) {
    fun observeAll(): Flow<List<FolderSessionEntity>> = dao.observeAll()
    suspend fun add(name: String, rootPath: String): Long = withContext(dispatcherProvider.io) {
        val now = System.currentTimeMillis()
        dao.insert(
            FolderSessionEntity(
                name = name,
                rootPath = rootPath,
                currentPath = rootPath,
                createdAt = now,
                lastAccess = now
            )
        )
    }
    suspend fun updateCurrentDir(id: Long, currentPath: String) = withContext(dispatcherProvider.io) {
        // 列级更新，避免整行覆盖把并发写入的 sortOption 回退
        dao.updateCurrentDir(id, currentPath, System.currentTimeMillis())
    }
    suspend fun updateSortOption(id: Long, sortOption: Int) = withContext(dispatcherProvider.io) {
        dao.updateSortOption(id, sortOption)
    }
    suspend fun switchTo(id: Long) = withContext(dispatcherProvider.io) {
        dao.touchLastAccess(id, System.currentTimeMillis())
    }
    suspend fun delete(id: Long) = withContext(dispatcherProvider.io) { dao.delete(id) }
    suspend fun getById(id: Long): FolderSessionEntity? = withContext(dispatcherProvider.io) { dao.getById(id) }
    suspend fun getAll(): List<FolderSessionEntity> = withContext(dispatcherProvider.io) { dao.getAll() }
}
