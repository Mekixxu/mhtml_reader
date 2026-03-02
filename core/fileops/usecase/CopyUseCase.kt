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
    ) = withContext(dispatcherProvider.io) {
        // (目录递归、文件进度)
        // 仅实现文件拷贝+目录递归
        TODO("需结合IFileSystem流式和输出能力实现具体copy逻辑。")
    }
}
