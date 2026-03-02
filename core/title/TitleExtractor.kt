package core.title

import core.vfs.model.VfsPath
import core.database.entity.enums.FileType
import java.io.File

/**
 * 标题提取器接口。所有操作在IO线程。允许返回null（提取失败/未找到title）。
 */
interface TitleExtractor {
    suspend fun extractTitle(
        source: VfsPath,
        cacheFile: File,
        fileType: FileType,
        maxBytesToRead: Long = 1024 * 256 // 默认最大256KB，可调
    ): String?
}
