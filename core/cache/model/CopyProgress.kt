package core.cache.model

/**
 * 拷贝进度反馈，用于 Flow/进度条
 */
data class CopyProgress(
    val copiedBytes: Long,
    val totalBytes: Long
)
