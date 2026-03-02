package core.reader.model

import core.database.entity.enums.FileType

/**
 * 阅读Tab状态，生命周期与缓存绑定。
 */
data class ReaderTab(
    val tabId: String,
    val sourcePathRaw: String,
    val fileType: FileType,
    val cacheKey: String?,
    val cacheFilePath: String?,
    val title: String?,
    val lastKnownProgress: Float,
    val lastKnownPageIndex: Int
)
