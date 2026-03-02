package core.favorites.domain.usecase

import core.data.repo.FavoritesRepository
import core.favorites.domain.validator.FavoritesTreeValidator
import core.database.entity.enums.SourceType
import core.common.DispatcherProvider
import kotlinx.coroutines.withContext

/**
 * 新增收藏项（文件），先校验name。
 */
class AddFavoriteFileUseCase(
    private val repo: FavoritesRepository,
    private val validator: FavoritesTreeValidator,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend fun add(parentId: Long?, name: String, path: String, sourceType: SourceType): Result<Long> =
        withContext(dispatcherProvider.io) {
            validator.assertValidName(name).getOrElse { return@withContext Result.failure(it) }
            val id = repo.addFile(parentId, name, path, sourceType)
            Result.success(id)
        }
}
