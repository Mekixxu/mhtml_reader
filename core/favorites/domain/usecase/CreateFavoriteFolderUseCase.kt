package core.favorites.domain.usecase

import core.data.repo.FavoritesRepository
import core.favorites.domain.validator.FavoritesTreeValidator
import core.common.DispatcherProvider
import kotlinx.coroutines.withContext

/**
 * 新建收藏夹用例，先校验name非空。
 */
class CreateFavoriteFolderUseCase(
    private val repo: FavoritesRepository,
    private val validator: FavoritesTreeValidator,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend fun create(parentId: Long?, name: String): Result<Long> = withContext(dispatcherProvider.io) {
        validator.assertValidName(name).getOrElse { return@withContext Result.failure(it) }
        val id = repo.addFolder(parentId, name)
        Result.success(id)
    }
}
