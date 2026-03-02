package core.vfs.model

/**
 * 统一文件元数据结构
 */
data class VfsEntry(
    val name: String,
    val path: VfsPath,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedEpochMs: Long,
    val mimeType: String? = null
)
