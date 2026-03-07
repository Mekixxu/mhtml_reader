package core.favorites.domain.model

import core.database.entity.FavoriteEntity
import core.database.entity.enums.FavoriteType
import core.database.entity.enums.SourceType

/**
 * 业务层节点模型，便于UI和UseCase交互。
 */
data class FavoriteNode(
    val id: Long,
    val parentId: Long?,
    val name: String,
    val type: FavoriteType,
    val path: String,
    val sourceType: SourceType,
    val createdAt: Long
) {
    companion object {
        fun fromEntity(entity: FavoriteEntity) = FavoriteNode(
            entity.id, entity.parentId, entity.name, entity.type, entity.path, entity.sourceType, entity.createdAt
        )
    }
}
