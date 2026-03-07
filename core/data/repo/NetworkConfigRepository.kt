package core.data.repo

import core.database.dao.NetworkConfigDao
import core.database.entity.NetworkConfigEntity
import core.common.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 网络配置Repo，add/update/delete返回id，observeAll全量流。
 */
class NetworkConfigRepository(
    private val dao: NetworkConfigDao,
    private val dispatcherProvider: DispatcherProvider
) {
    fun observeAll(): Flow<List<NetworkConfigEntity>> = dao.observeAll()
    suspend fun add(entity: NetworkConfigEntity): Long = withContext(dispatcherProvider.io) { dao.insert(entity) }
    suspend fun update(entity: NetworkConfigEntity) = withContext(dispatcherProvider.io) { dao.update(entity) }
    suspend fun delete(id: Long) = withContext(dispatcherProvider.io) { dao.delete(id) }
    suspend fun getAll(): List<NetworkConfigEntity> = withContext(dispatcherProvider.io) { dao.getAll() }
    suspend fun getById(id: Long): NetworkConfigEntity? = withContext(dispatcherProvider.io) { dao.getById(id) }
}
