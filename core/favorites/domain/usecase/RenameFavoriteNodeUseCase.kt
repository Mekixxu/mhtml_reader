package core.favorites.domain.usecase

import core.data.repo.FavoritesRepository
import core.favorites.domain.validator.FavoritesTreeValidator
import core.common.DispatcherProvider
import kotlinx.coroutines.withContext

/**
 * 节点重命名用例，先校验name。
 */
class RenameFavoriteNodeUseCase(
    private val repo: FavoritesRepository,
    private val validator: FavoritesTreeValidator,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend fun rename(id: Long, newName: String): Result<Unit> = withContext(dispatcherProvider.io) {
        validator.assertValidName(newName).getOrElse { return@withContext Result.failure(it) }
        repo.rename(id, newName)
        Result.success(Unit)
    }
}
