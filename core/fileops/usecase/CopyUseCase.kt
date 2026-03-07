package core.fileops.usecase

import core.vfs.IFileSystem
import core.vfs.model.VfsPath
import core.fileops.model.ConflictStrategy
import core.fileops.model.FileOpState
import core.fileops.util.NameConflictResolver
import core.common.DispatcherProvider
import kotlinx.coroutines.withContext

/**
 * Copy流程。支持进度、冲突处理、可取消，文件夹递归。
 */
class CopyUseCase(
    private val fileSystem: IFileSystem,
    private val nameResolver: NameConflictResolver,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend fun copy(
        from: VfsPath,
        toDir: VfsPath,
        strategy: ConflictStrategy,
        emit: suspend (FileOpState) -> Unit
    ): Unit = withContext(dispatcherProvider.io) {
        emit(FileOpState.Progress(0L, -1L))
        val sourceName = extractName(from)
        val resolvedPath = nameResolver.resolve(toDir, sourceName, strategy)
        val defaultTarget = core.fileops.util.FileNameUtils.childPath(toDir, sourceName)
        var copied = fileSystem.copy(from, toDir).getOrElse { throw it }
        if (resolvedPath.raw != defaultTarget.raw) {
            val resolvedName = extractName(resolvedPath)
            copied = fileSystem.rename(copied, resolvedName).getOrElse { throw it }
        }
        emit(FileOpState.Progress(1L, 1L))
        emit(FileOpState.Success(copied))
    }

    private fun extractName(path: VfsPath): String =
        path.raw.replace('\\', '/').substringAfterLast('/').ifBlank { "file" }
}
