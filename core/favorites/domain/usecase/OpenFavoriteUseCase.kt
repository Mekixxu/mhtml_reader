package core.favorites.domain.usecase

import core.data.repo.FavoritesRepository
import core.reader.model.OpenRequest
import core.reader.usecase.InferFileTypeUseCase
import core.vfs.model.VfsPath
import core.common.DispatcherProvider
import kotlinx.coroutines.withContext

/**
 * 收藏项打开用例，路径转OpenRequest。
 *
 * Deprecated: 当前 UI 尚未接线该用例；保留用于后续收藏树与阅读器直连。
 */
@Deprecated("未接线：当前收藏页通过 MainActivity 路径直接打开，后续接入收藏树时再启用")
class OpenFavoriteUseCase(
    private val repo: FavoritesRepository,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend fun open(favoriteId: Long): Result<OpenRequest> = withContext(dispatcherProvider.io) {
        val entity = repo.getById(favoriteId) ?: return@withContext Result.failure(core.common.AppError.NotFound)
        val fileType = InferFileTypeUseCase.infer(entity.path)
        val vfsPath = VfsPath.LocalFile(entity.path) // todo: sourceType适配
        Result.success(OpenRequest(
            source = vfsPath,
            fileName = entity.name,
            fileType = fileType,
            openMode = core.reader.model.OpenMode.NEW_TAB
        ))
    }
}
