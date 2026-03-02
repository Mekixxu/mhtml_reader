package core.domain.usecase

import core.common.DispatcherProvider
import core.domain.model.SortOption
import core.vfs.IFileSystem
import core.vfs.model.VfsEntry
import core.vfs.model.VfsPath
import kotlinx.coroutines.withContext

/**
 * 仅列出元数据，不触发 Title 补齐。
 * 注意：过滤应按文件扩展名（mhtml/mht/html/htm/pdf）。
 */
class ListDirectoryUseCase(
    private val fileSystem: IFileSystem,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend fun list(
        dir: VfsPath,
        allowedExtensions: Set<String>,
        sort: SortOption
    ): List<VfsEntry> = withContext(dispatcherProvider.io) {
        val all = fileSystem.list(dir) // 以包1真实签名为准
        val filtered = all.filter { e ->
            val ext = e.name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
            ext in allowedExtensions
        }

        when (sort) {
            SortOption.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            SortOption.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
            SortOption.SIZE_ASC -> filtered.sortedBy { it.sizeBytes }
            SortOption.SIZE_DESC -> filtered.sortedByDescending { it.sizeBytes }
            SortOption.MODIFIED_ASC -> filtered.sortedBy { it.lastModifiedEpochMs }
            SortOption.MODIFIED_DESC -> filtered.sortedByDescending { it.lastModifiedEpochMs }
        }
    }
}

