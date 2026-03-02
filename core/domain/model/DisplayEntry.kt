package core.domain.model

import core.vfs.model.VfsPath

/**
 * 文件列表展示项，同时持有文件名与真实提取title。
 */
data class DisplayEntry(
    val path: VfsPath,
    val fileName: String,
    val displayTitle: String,
    val lastModified: Long,
    val sizeBytes: Long
)
