package core.favorites.domain.model

/**
 * 用于面包屑显示/root追溯
 */
data class BreadcrumbItem(
    val id: Long,
    val name: String,
    val type: String
)
