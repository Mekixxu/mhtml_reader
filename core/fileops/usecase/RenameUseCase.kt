package core.fileops.usecase

import core.vfs.IFileSystem
import core.vfs.model.VfsPath
import core.common.DispatcherProvider
import core.fileops.model.FileOpState
import kotlinx.coroutines.withContext

/**
 * 只改名不动目录。因文件系统特性，可能不是原子。建议严格使用此用例。
 */
class RenameUseCase(
    private val fileSystem: IFileSystem,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend fun rename(
        target: VfsPath,
        newName: String,
        emit: suspend (FileOpState) -> Unit
    ) = withContext(dispatcherProvider.io) {
        val result = fileSystem.rename(target, newName).getOrElse { throw it }
        emit(FileOpState.Success(result))
    }
}
