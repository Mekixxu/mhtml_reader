package core.reader.model

import core.vfs.model.VfsPath
import core.database.entity.enums.FileType

/**
 * 文件阅读请求信息（兼容所有来源）
 */
data class OpenRequest(
    val source: VfsPath,
    val fileName: String,
    val fileType: FileType,
    val openMode: OpenMode = OpenMode.NEW_TAB,
    val referrerTabId: String? = null,
    val background: Boolean = false
)

enum class OpenMode { NEW_TAB, REUSE_CURRENT }
