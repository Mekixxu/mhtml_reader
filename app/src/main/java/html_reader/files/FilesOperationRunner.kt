package com.html_reader.files

import core.fileops.model.FileOpRequest
import core.fileops.model.FileOpState
import core.fileops.usecase.ExecuteFileOpUseCase
import core.vfs.model.VfsPath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

data class FilesOpProgress(
    val indeterminate: Boolean,
    val max: Int = 0,
    val current: Int = 0
)

object FilesOperationRunner {
    suspend fun run(
        useCase: ExecuteFileOpUseCase,
        request: FileOpRequest,
        onStarted: () -> Unit,
        onProgress: (FilesOpProgress) -> Unit,
        onSuccessPath: (VfsPath?) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            useCase.execute(request).collect { state ->
                when (state) {
                    is FileOpState.Started -> onStarted()
                    is FileOpState.Progress -> {
                        if (state.total > 0) {
                            val total = state.total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                            val current = state.current.coerceAtMost(total.toLong()).toInt()
                            onProgress(FilesOpProgress(indeterminate = false, max = total, current = current))
                        } else {
                            onProgress(FilesOpProgress(indeterminate = true))
                        }
                    }
                    is FileOpState.Success -> onSuccessPath(state.resultPath)
                    is FileOpState.Error -> onError(state.error.message ?: state.error.javaClass.simpleName)
                }
            }
        } catch (e: CancellationException) {
            // 取消是生命周期正常行为，必须重抛，由调用方决定是否清理 UI
            throw e
        } catch (t: Throwable) {
            onError(t.message ?: t.javaClass.simpleName)
        }
    }
}
