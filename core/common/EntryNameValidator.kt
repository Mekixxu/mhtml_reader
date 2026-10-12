package core.common

/**
 * 单文件/单目录名称校验。
 *
 * 只校验名称本身，不处理完整路径；完整路径由各 VFS 实现负责隔离。
 * 拒绝空、"."、".."、路径分隔符、NUL 及控制字符；SMB 额外拒绝 Windows 保留字符。
 */
object EntryNameValidator {
    private val COMMON_FORBIDDEN = setOf('/', '\\', '\u0000')

    fun isValid(name: String): Boolean {
        if (name.isBlank()) return false
        if (name == "." || name == "..") return false
        return name.none { it in COMMON_FORBIDDEN || it.isISOControl() }
    }

    fun isValidSmb(name: String): Boolean {
        if (!isValid(name)) return false
        return name.none { it in SMB_FORBIDDEN }
    }

    private val SMB_FORBIDDEN = setOf(':', '*', '?', '"', '<', '>', '|')
}
