package core.favorites.domain.usecase

import core.data.repo.FavoritesRepository
import core.favorites.domain.model.BreadcrumbItem
import core.common.DispatcherProvider
import kotlinx.coroutines.withContext

/**
 * 面包屑路径追溯，从节点到root。
 */
class GetFavoriteBreadcrumbUseCase(
    private val repo: FavoritesRepository,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend fun getBreadcrumb(id: Long): List<BreadcrumbItem> = withContext(dispatcherProvider.io) {
        val path = mutableListOf<BreadcrumbItem>()
        var current = repo.getById(id)
        while (current != null) {
            path.add(0, BreadcrumbItem(current.id, current.name, current.type.name))
            current = current.parentId?.let { repo.getById(it) }
        }
        path
    }
}
