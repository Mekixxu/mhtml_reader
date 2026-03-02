package core.data.repo

import core.database.dao.TitleCacheDao
import core.database.entity.TitleCacheEntity
import core.common.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TitleCacheRepository(
    private val dao: TitleCacheDao,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend fun upsert(entity: TitleCacheEntity) = withContext(dispatcherProvider.io) { dao.upsert(entity) }
    suspend fun get(path: String): TitleCacheEntity? = withContext(dispatcherProvider.io) { dao.get(path) }
    fun observe(path: String): Flow<TitleCacheEntity?> = dao.observe(path)
    suspend fun delete(path: String) = withContext(dispatcherProvider.io) { dao.delete(path) }
    suspend fun clearAll() = withContext(dispatcherProvider.io) { dao.clearAll() }
    suspend fun invalidateIfStale(path: String, lastModified: Long, newTitle: String) = withContext(dispatcherProvider.io) {
        val cached = dao.get(path)
        if (cached == null || cached.lastModified != lastModified) {
            dao.upsert(
                TitleCacheEntity(
                    path = path,
                    title = newTitle,
                    lastModified = lastModified,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }
    suspend fun deleteOlderThan(epoch: Long) = withContext(dispatcherProvider.io) { dao.deleteOlderThan(epoch) }
    suspend fun getAll(): List<TitleCacheEntity> = withContext(dispatcherProvider.io) { dao.getAll() }
}
