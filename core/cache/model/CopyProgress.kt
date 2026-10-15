package core.cache.model

/**
 * 拷贝进度反馈，用于 Flow/进度条
 *
 * @param copiedBytes 已实际写入缓存的字节数
 * @param totalBytes 已知的源文件总字节数；`null` 表示长度未知（如部分 SAF/网络源），
 *   此时上游应按不确定进度展示，不能臆测完成比例
 */
data class CopyProgress(
    val copiedBytes: Long,
    val totalBytes: Long?
)
