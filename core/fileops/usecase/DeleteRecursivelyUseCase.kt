package core.fileops.usecase

import core.vfs.IFileSystem
import core.vfs.model.VfsPath
import core.common.DispatcherProvider
import core.fileops.model.FileOpState
import kotlinx.coroutines.withContext

/**
 * 递归删除文件夹。迭代实现防止StackOverflow。
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
        // 迭代实现
        val stack = ArrayDeque<VfsPath>()
        stack.add(target)
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            val entry = fileSystem.list(cur).getOrNull()
            if (entry != null && entry.isNotEmpty()) {
                entry.forEach {
                    if (it.isDirectory) stack.add(it.path)
                    else fileSystem.delete(it.path)
                }
            }
            fileSystem.delete(cur)
        }
        emit(FileOpState.Success(target))
    }
}
