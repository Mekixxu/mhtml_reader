package core.files.domain.model

import core.vfs.model.VfsPath

/**
 * FolderSession领域模型。对应持久化表/实体，currentDir每次浏览变更。
 */
data class FolderSession(
    val id: Long,
    val name: String,
    val rootDir: VfsPath,
    val currentDir: VfsPath,
    val createdAt: Long,
    val lastAccess: Long
)
