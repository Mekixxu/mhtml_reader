package core.favorites.domain.usecase

import core.data.repo.FavoritesRepository
import core.database.entity.enums.FavoriteType
import core.common.DispatcherProvider
import kotlinx.coroutines.withContext

/**
 * 删除节点（文件夹为deleteSubtree，文件为deleteById）
 */
class DeleteFavoriteNodeUseCase(
    private val repo: FavoritesRepository,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend fun delete(id: Long): Result<Unit> = withContext(dispatcherProvider.io) {
        val node = repo.getById(id) ?: return@withContext Result.failure(core.common.AppError.NotFound)
        if (node.type == FavoriteType.FOLDER)
            repo.deleteSubtree(id)
        else
            repo.deleteById(id)
        Result.success(Unit)
    }
}
