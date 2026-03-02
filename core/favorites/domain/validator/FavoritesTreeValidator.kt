package core.favorites.domain.validator

import core.data.repo.FavoritesRepository
import core.common.AppError
import kotlinx.coroutines.withContext
import core.common.DispatcherProvider

/**
 * 校验收藏树结构操作是否合理（防环/根/空名）。
 * 防止出现循环（移动到自身或子孙），防空名。
 */
class FavoritesTreeValidator(
    private val repo: FavoritesRepository,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend fun assertCanMove(nodeId: Long, newParentId: Long?): Result<Unit> =
        withContext(dispatcherProvider.io) {
            if (nodeId == newParentId) return@withContext Result.failure(AppError.Conflict)
            // newParentId=null允许，表示移到根
            var current = newParentId
            while (current != null) {
                if (current == nodeId) return@withContext Result.failure(AppError.Conflict)
                val parent = repo.getById(current)?.parentId
                current = parent
            }
            Result.success(Unit)
        }

    suspend fun assertValidName(name: String): Result<Unit> {
        if (name.trim().isEmpty()) return Result.failure(AppError.UnsupportedOperation)
        return Result.success(Unit)
    }
}
