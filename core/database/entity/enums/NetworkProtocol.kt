package core.database.entity.enums

enum class NetworkProtocol { SMB, FTP }

// 安全转换，防valueOf异常
fun fromSafeNetworkProtocol(value: String?): NetworkProtocol = try {
    if (value == null) NetworkProtocol.SMB else NetworkProtocol.valueOf(value)
} catch (_: Exception) { NetworkProtocol.SMB }
