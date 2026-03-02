package core.favorites.domain.usecase

import core.data.repo.FavoritesRepository
import core.favorites.domain.validator.FavoritesTreeValidator
import core.common.DispatcherProvider
import kotlinx.coroutines.withContext

/**
 * 节点移动用例，防环校验。
 */
class MoveFavoriteNodeUseCase(
    private val repo: FavoritesRepository,
    private val validator: FavoritesTreeValidator,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend fun move(id: Long, newParentId: Long?): Result<Unit> = withContext(dispatcherProvider.io) {
        validator.assertCanMove(id, newParentId).getOrElse { return@withContext Result.failure(it) }
        repo.move(id, newParentId)
        Result.success(Unit)
    }
}
