package core.data.repo

import core.common.DispatcherProvider
import core.database.dao.FavoriteDao
import core.database.entity.FavoriteEntity
import core.database.entity.enums.FavoriteType
import core.database.entity.enums.SourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class FavoritesRepository(
    private val dao: FavoriteDao,
    private val dispatcherProvider: DispatcherProvider
) {
    fun observeChildren(parentId: Long?): Flow<List<FavoriteEntity>> = dao.observeChildren(parentId)

    suspend fun addFolder(parentId: Long?, name: String): Long = withContext(dispatcherProvider.io) {
        dao.insert(
            FavoriteEntity(
                parentId = parentId,
                name = name,
                type = FavoriteType.FOLDER,
                path = "",
                sourceType = SourceType.LOCAL,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun addFile(parentId: Long?, name: String, path: String, sourceType: SourceType): Long =
        withContext(dispatcherProvider.io) {
            dao.insert(
                FavoriteEntity(
                    parentId = parentId,
                    name = name,
                    type = FavoriteType.FILE,
                    path = path,
                    sourceType = sourceType,
                    createdAt = System.currentTimeMillis()
                )
            )
        }

    suspend fun move(id: Long, newParentId: Long?) =
        withContext(dispatcherProvider.io) { dao.updateParent(id, newParentId) }

    suspend fun rename(id: Long, newName: String) =
        withContext(dispatcherProvider.io) { dao.rename(id, newName) }

    suspend fun deleteSubtree(id: Long) =
        withContext(dispatcherProvider.io) { dao.deleteSubtree(id) }

    suspend fun getById(id: Long): FavoriteEntity? =
        withContext(dispatcherProvider.io) { dao.getById(id) }

    suspend fun getAll(): List<FavoriteEntity> =
        withContext(dispatcherProvider.io) { dao.getAll() }

    suspend fun getChildren(parentId: Long?): List<FavoriteEntity> =
        withContext(dispatcherProvider.io) { dao.getChildren(parentId) }

    suspend fun clearAll() =
        withContext(dispatcherProvider.io) { dao.clearAll() }
}

