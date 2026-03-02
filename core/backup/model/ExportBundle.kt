package core.backup.model

import core.database.entity.enums.*
import kotlinx.serialization.Serializable

@Serializable
data class ExportBundle(
    val schemaVersion: Int = 1,
    val exportedAt: Long,
    val favorites: List<FavoriteDto>,
    val history: List<HistoryDto>,
    val networkConfigs: List<NetworkConfigDto>,
    val titleCache: List<TitleCacheDto>
)

@Serializable
data class FavoriteDto(
    val id: Long? = null,
    val parentId: Long? = null,
    val name: String,
    val type: String,
    val path: String,
    val sourceType: String,
    val createdAt: Long
)

@Serializable
data class HistoryDto(
    val path: String,
    val title: String,
    val lastAccess: Long,
    val progress: Float,
    val pageIndex: Int,
    val fileType: String
)

@Serializable
data class NetworkConfigDto(
    val id: Long? = null,
    val name: String,
    val protocol: String,
    val host: String,
    val port: Int,
    val username: String,
    /**
     * 明文密码警告：仅v1.0，生产必须加密！
     */
    val password: String,
    val defaultPath: String
)

@Serializable
data class TitleCacheDto(
    val path: String,
    val title: String,
    val lastModified: Long,
    val updatedAt: Long
)
