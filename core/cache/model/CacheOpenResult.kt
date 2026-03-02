package core.cache.model

import core.vfs.model.VfsPath
import java.io.File

enum class ContentType { MHTML, PDF, HTML, WEB, UNKNOWN }

/**
 * 打开缓存结果，提供给阅读器等上层直接用
 */
data class CacheOpenResult(
    val cacheFile: File,
    val cacheKey: String,
    val sourcePath: VfsPath,
    val contentType: ContentType
)
