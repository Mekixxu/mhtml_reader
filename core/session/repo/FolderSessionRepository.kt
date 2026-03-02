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
        dao.getById(id)?.let {
            dao.update(it.copy(currentPath = currentPath, lastAccess = System.currentTimeMillis()))
        }
    }
    suspend fun switchTo(id: Long) = withContext(dispatcherProvider.io) {
        dao.getById(id)?.let {
            dao.update(it.copy(lastAccess = System.currentTimeMillis()))
        }
    }
    suspend fun delete(id: Long) = withContext(dispatcherProvider.io) { dao.delete(id) }
    suspend fun getById(id: Long): FolderSessionEntity? = withContext(dispatcherProvider.io) { dao.getById(id) }
    suspend fun getAll(): List<FolderSessionEntity> = withContext(dispatcherProvider.io) { dao.getAll() }
}
