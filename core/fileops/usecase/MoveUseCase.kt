package core.fileops.usecase

import core.vfs.IFileSystem
import core.vfs.model.VfsPath
import core.fileops.model.ConflictStrategy
import core.fileops.util.NameConflictResolver
import core.common.DispatcherProvider
import core.fileops.model.FileOpState
import kotlinx.coroutines.withContext

/**
 * Move流程。支持冲突命名，跨目录move时自动退级为copy+delete。
 */
class MoveUseCase(
    private val fileSystem: IFileSystem,
    private val nameResolver: NameConflictResolver,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend fun move(
        from: VfsPath,
        toDir: VfsPath,
        strategy: ConflictStrategy,
        emit: suspend (FileOpState) -> Unit
    ): Unit = withContext(dispatcherProvider.io) {
        emit(FileOpState.Progress(0L, -1L))
        val sourceName = extractName(from)
        val resolvedPath = nameResolver.resolve(toDir, sourceName, strategy)
        val defaultTarget = core.fileops.util.FileNameUtils.childPath(toDir, sourceName)
        var moved = fileSystem.move(from, toDir).getOrElse { throw it }
        if (resolvedPath.raw != defaultTarget.raw) {
            val resolvedName = extractName(resolvedPath)
            moved = fileSystem.rename(moved, resolvedName).getOrElse { throw it }
        }
        emit(FileOpState.Progress(1L, 1L))
        emit(FileOpState.Success(moved))
    }

    private fun extractName(path: VfsPath): String =
        path.raw.replace('\\', '/').substringAfterLast('/').ifBlank { "file" }
}
