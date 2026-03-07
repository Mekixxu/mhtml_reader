package core.fileops.usecase

import core.vfs.IFileSystem
import core.fileops.model.*
import core.fileops.util.NameConflictResolver
import core.common.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CancellationException

/**
 * 统一文件操作入口。支持进度/cancel/冲突自动命名/错误统一封装。
 */
class ExecuteFileOpUseCase(
    private val fileSystem: IFileSystem,
    private val nameResolver: NameConflictResolver,
    private val dispatcherProvider: DispatcherProvider
) {
    fun execute(request: FileOpRequest): Flow<FileOpState> = flow {
        emit(FileOpState.Started)
        try {
            when (request) {
                is FileOpRequest.CreateFolder -> {
                    val child = core.fileops.util.FileNameUtils.childPath(request.parentDir, request.name)
                    fileSystem.createFolder(child)
                        .getOrElse { throw it }
                    emit(FileOpState.Success(child))
                }
                is FileOpRequest.Delete -> {
                    DeleteRecursivelyUseCase(fileSystem, dispatcherProvider).delete(
                        request.target,
                        request.recursive
                    ) { state -> emit(state) }
                }
                is FileOpRequest.Rename -> {
                    val renamed = fileSystem.rename(request.target, request.newName).getOrElse { throw it }
                    emit(FileOpState.Success(renamed))
                }
                is FileOpRequest.Copy -> {
                    CopyUseCase(fileSystem, nameResolver, dispatcherProvider)
                        .copy(request.from, request.toDir, request.conflict) { state -> emit(state) }
                }
                is FileOpRequest.Move -> {
                    MoveUseCase(fileSystem, nameResolver, dispatcherProvider)
                        .move(request.from, request.toDir, request.conflict) { state -> emit(state) }
                }
            }
        } catch (e: CancellationException) {
            emit(FileOpState.Error(core.common.AppError.IoError("操作被取消", e)))
        } catch (e: Exception) {
            emit(FileOpState.Error(core.common.AppError.IoError(e.message, e)))
        }
    }
}
