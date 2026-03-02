package core.files.domain.model

import core.vfs.model.VfsEntry
import core.vfs.model.VfsPath
import core.domain.model.SortOption

/**
 * 当前会话目录及筛选结果。
 */
data class DirectoryState(
    val sessionId: Long,
    val currentDir: VfsPath,
    val entries: List<VfsEntry>,
    val sort: SortOption,
    val query: String
)
