package core.fileops.model

/**
 * 文件名冲突策略
 */
enum class ConflictStrategy {
    FAIL,
    AUTO_RENAME,
    // OVERWRITE // 可扩展
}
