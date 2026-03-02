package core.fileops.usecase

import core.vfs.IFileSystem
import core.vfs.model.VfsPath
import core.common.DispatcherProvider
import core.fileops.util.NameConflictResolver
import core.fileops.model.ConflictStrategy
import core.fileops.model.FileOpState
import kotlinx.coroutines.withContext

/**
 * 新建目录，处理冲突命名策略。
 */
class CreateFolderUseCase(
    private val fileSystem: IFileSystem,
    private val nameResolver: NameConflictResolver,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend fun createFolder(
        parentDir: VfsPath,
        name: String,
        strategy: ConflictStrategy,
        emit: suspend (FileOpState) -> Unit
    ) = withContext(dispatcherProvider.io) {
        val resolved = nameResolver.resolve(parentDir, name, strategy)
        fileSystem.createFolder(resolved)
            .getOrElse { throw it }
        emit(FileOpState.Success(resolved))
    }
}
