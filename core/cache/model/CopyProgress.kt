package core.cache.model

import core.cache.CacheLease

/**
 * 拷贝进度反馈，用于 Flow/进度条
 *
 * @param copiedBytes 已实际写入缓存的字节数
 * @param totalBytes 已知的源文件总字节数；`null` 表示长度未知（如部分 SAF/网络源），
 *   此时上游应按不确定进度展示，不能臆测完成比例
 * @param lease 本次「打开缓存」的保护租约：缓存命中或拷贝开始时取得，持续到调用方
 *   `transferToTab`（移交给标签）或 `release`（去重/失败/取消等放弃路径）。
 *   `null` 表示当前帧不携带租约（例如未启用租约时的兼容路径）。
 */
data class CopyProgress(
    val copiedBytes: Long,
    val totalBytes: Long?,
    val lease: CacheLease? = null
)
