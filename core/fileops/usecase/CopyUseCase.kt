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
        // 冲突时直接复制到解析后的目标名，避免先写默认名再改名导致覆盖
        val copied = if (resolvedPath.raw == defaultTarget.raw) {
            fileSystem.copy(from, toDir).getOrElse { throw it }
        } else {
            fileSystem.copyTo(from, resolvedPath).getOrElse { throw it }
        }
        emit(FileOpState.Progress(1L, 1L))
        emit(FileOpState.Success(copied))
    }

    private fun extractName(path: VfsPath): String =
        path.raw.replace('\\', '/').substringAfterLast('/').ifBlank { "file" }
}
