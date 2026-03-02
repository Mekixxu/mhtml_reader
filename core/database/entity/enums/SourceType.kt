package core.database.entity.enums

enum class SourceType { LOCAL, SMB, FTP, WEB, UNKNOWN }

// 安全转换，防valueOf异常
fun fromSafeSourceType(value: String?): SourceType = try {
    if (value == null) SourceType.UNKNOWN else SourceType.valueOf(value)
} catch (_: Exception) { SourceType.UNKNOWN }
