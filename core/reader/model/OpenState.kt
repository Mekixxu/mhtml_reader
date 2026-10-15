package core.reader.model

import core.common.AppError
import core.reader.model.ReaderTab

/**
 * 打开流程状态。流式推送loading/拷贝/ready/error。
 */
sealed class OpenState {
    object Loading : OpenState()
    /** [totalBytes] 为 `null` 表示源长度未知，UI 应以不确定进度展示 */
    data class Copying(val bytesCopied: Long, val totalBytes: Long?) : OpenState()
    data class Ready(val tab: ReaderTab) : OpenState()
    data class Error(val error: AppError) : OpenState()
}
