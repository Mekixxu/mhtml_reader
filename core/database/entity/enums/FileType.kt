package core.database.entity.enums

enum class FileType { MHTML, HTML, PDF, WEB }

// 安全转换，防valueOf异常
fun fromSafeFileType(value: String?): FileType = try {
    if (value == null) FileType.WEB else FileType.valueOf(value)
} catch (_: Exception) { FileType.WEB }
