package core.fileops.usecase

import core.vfs.IFileSystem
import core.vfs.model.VfsEntry
import core.vfs.model.VfsPath
import core.common.DispatcherProvider
import core.fileops.model.FileOpState
import kotlinx.coroutines.withContext

/**
 * 递归删除文件夹。迭代实现防止StackOverflow。
 *
 * 每个目录先完整分页读取自身条目，再删除文件、处理子目录，最后删除目录本身。
 * 任何 list/delete 失败都会向上抛出，由 ExecuteFileOpUseCase 统一转换为 Error 状态。
 */
class DeleteRecursivelyUseCase(
    private val fileSystem: IFileSystem,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend fun delete(
        target: VfsPath,
        recursive: Boolean = true,
        emit: suspend (FileOpState) -> Unit
    ) = withContext(dispatcherProvider.io) {
        if (!recursive) {
            fileSystem.delete(target).getOrElse { throw it }
            emit(FileOpState.Success(target))
            return@withContext
        }

        // list 成功表示目标是目录；失败则按文件删除处理。目录删除失败会作为错误上报，
        // 不会再出现「只删了前 100 项却报成功」。
        val firstPage = fileSystem.list(target, offset = 0, limit = PAGE_SIZE)
        if (firstPage.isFailure) {
            fileSystem.delete(target).getOrElse { throw it }
            emit(FileOpState.Success(target))
            return@withContext
        }

        val rootEntries = listRemainingPages(target, firstPage.getOrThrow())
        val stack = ArrayDeque<DeleteNode>()
        stack.add(DeleteNode(target, entries = rootEntries))

        while (stack.isNotEmpty()) {
            val node = stack.last()
            if (!node.visited) {
                node.visited = true
                (node.entries ?: listAll(node.path)).forEach { entry ->
                    if (entry.isDirectory) {
                        stack.add(DeleteNode(entry.path))
                    } else {
                        fileSystem.delete(entry.path).getOrElse { throw it }
                    }
                }
            } else {
                fileSystem.delete(node.path).getOrElse { throw it }
                stack.removeLast()
            }
        }
        emit(FileOpState.Success(target))
    }

    private suspend fun listAll(dir: VfsPath): List<VfsEntry> {
        val firstPage = fileSystem.list(dir, offset = 0, limit = PAGE_SIZE)
            .getOrElse { throw it }
        return listRemainingPages(dir, firstPage)
    }

    private suspend fun listRemainingPages(
        dir: VfsPath,
        firstPage: List<VfsEntry>
    ): List<VfsEntry> {
        val all = firstPage.toMutableList()
        var page = firstPage
        var offset = firstPage.size
        while (page.size == PAGE_SIZE) {
            page = fileSystem.list(dir, offset = offset, limit = PAGE_SIZE)
                .getOrElse { throw it }
            all += page
            offset += page.size
        }
        return all
    }

    private data class DeleteNode(
        val path: VfsPath,
        val entries: List<VfsEntry>? = null,
        var visited: Boolean = false
    )

    private companion object {
        const val PAGE_SIZE = 100
    }
}
