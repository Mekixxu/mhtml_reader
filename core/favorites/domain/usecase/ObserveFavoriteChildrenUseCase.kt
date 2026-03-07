package core.favorites.domain.usecase

import core.data.repo.FavoritesRepository
import core.favorites.domain.model.FavoriteNode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 观察子节点流，自动实时更新。
 */
class ObserveFavoriteChildrenUseCase(
    private val repo: FavoritesRepository
) {
    fun observe(parentId: Long?): Flow<List<FavoriteNode>> =
        repo.observeChildren(parentId).map { it.map(FavoriteNode::fromEntity) }
}
