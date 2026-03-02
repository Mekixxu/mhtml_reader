package core.fileops.model

import core.common.AppError
import core.vfs.model.VfsPath

/**
 * 文件操作流程状态
 */
sealed class FileOpState {
    object Started : FileOpState()
    data class Progress(val current: Long, val total: Long) : FileOpState() // total -1 表示未知
    data class Success(val resultPath: VfsPath? = null) : FileOpState()
    data class Error(val error: AppError) : FileOpState()
}
