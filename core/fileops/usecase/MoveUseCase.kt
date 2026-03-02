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
    ) = withContext(dispatcherProvider.io) {
        // 若本地操作支持原子，直接move，否则copy+delete
        // 回退后实现copy+delete，需做好错误/回滚处理
        TODO("需实现move流程，可copy+delete+emit进度+清理")
    }
}
