package core.data.repo

import core.database.dao.NetworkConfigDao
import core.database.entity.NetworkConfigEntity
import core.common.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class NetworkConfigRepository(
    private val dao: NetworkConfigDao,
    private val dispatcherProvider: DispatcherProvider
) {
    fun observeAll(limit: Int = 100, offset: Int = 0): Flow<List<NetworkConfigEntity>> = dao.observeAll(limit, offset)
    suspend fun add(entity: NetworkConfigEntity) = withContext(dispatcherProvider.io) { dao.insert(entity) }
    suspend fun update(entity: NetworkConfigEntity) = withContext(dispatcherProvider.io) { dao.update(entity) }
    suspend fun delete(id: Long) = withContext(dispatcherProvider.io) { dao.delete(id) }
    suspend fun getAll(limit: Int = 100, offset: Int = 0): List<NetworkConfigEntity> = withContext(dispatcherProvider.io) {
        dao.getAll(limit, offset)
    }
    suspend fun getById(id: Long): NetworkConfigEntity? = withContext(dispatcherProvider.io) { dao.getById(id) }
    suspend fun clearAll() = withContext(dispatcherProvider.io) { dao.clearAll() }
}
