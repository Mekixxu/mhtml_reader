package core.data.repo

import core.common.UrlCredentialSanitizer
import core.database.dao.FavoriteDao
import core.database.entity.FavoriteEntity
import core.database.entity.enums.FavoriteType
import core.database.entity.enums.SourceType
import core.common.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 收藏夹 Repo 增补：deleteById、getById、observeChildren、getChildren。
 */
class FavoritesRepository(
    private val dao: FavoriteDao,
    private val dispatcherProvider: DispatcherProvider
) {
    fun observeChildren(parentId: Long?): Flow<List<FavoriteEntity>> = dao.observeChildren(parentId)

    fun observeAll(): Flow<List<FavoriteEntity>> = dao.observeAll()

    suspend fun getChildren(parentId: Long?): List<FavoriteEntity> = withContext(dispatcherProvider.io) {
        dao.getChildren(parentId)
    }

    // 说明：DAO 的 suspend 方法自带调度；不额外 withContext(io)，
    // 以便在 Room 事务中调用（事务是线程约束的）。
    suspend fun addFolder(parentId: Long?, name: String): Long {
        return dao.insert(
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

    // 安全：写入口统一剥离 URL 密码，任何调用方都不可能把明文凭据存入收藏
    suspend fun addFile(parentId: Long?, name: String, path: String, sourceType: SourceType): Long {
        val safePath = UrlCredentialSanitizer.stripPassword(path)
        val existing = dao.findByPath(
            parentId = parentId,
            path = safePath,
            sourceType = sourceType,
            type = FavoriteType.FILE
        )
        if (existing != null) {
            return existing.id
        }
        return dao.insert(
            FavoriteEntity(
                parentId = parentId,
                name = name,
                type = FavoriteType.FILE,
                path = safePath,
                sourceType = sourceType,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun addDirectory(parentId: Long?, name: String, path: String, sourceType: SourceType): Long {
        val safePath = UrlCredentialSanitizer.stripPassword(path)
        val existing = dao.findByPath(
            parentId = parentId,
            path = safePath,
            sourceType = sourceType,
            type = FavoriteType.FOLDER
        )
        if (existing != null) {
            return existing.id
        }
        return dao.insert(
            FavoriteEntity(
                parentId = parentId,
                name = name,
                type = FavoriteType.FOLDER,
                path = safePath,
                sourceType = sourceType,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * 一次性清洗历史版本入库的明文凭据收藏（幂等）。
     */
    suspend fun migrateLegacyCredentialsIfNeeded() {
        dao.getAll().forEach { favorite ->
            val sanitized = UrlCredentialSanitizer.stripPassword(favorite.path)
            if (sanitized != favorite.path) {
                dao.updatePath(favorite.id, sanitized)
            }
        }
    }

    suspend fun move(id: Long, newParentId: Long?) = withContext(dispatcherProvider.io) { dao.updateParent(id, newParentId) }
    suspend fun rename(id: Long, newName: String) = withContext(dispatcherProvider.io) { dao.rename(id, newName) }
    suspend fun deleteSubtree(id: Long) = withContext(dispatcherProvider.io) { dao.deleteSubtree(id) }
    suspend fun deleteById(id: Long) = withContext(dispatcherProvider.io) { dao.deleteById(id) }
    suspend fun getById(id: Long): FavoriteEntity? = withContext(dispatcherProvider.io) { dao.getById(id) }
    suspend fun getAll(): List<FavoriteEntity> = withContext(dispatcherProvider.io) { dao.getAll() }
    suspend fun clearAll() = dao.clearAll()
}
