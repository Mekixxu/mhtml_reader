package core.network.domain.model

/**
 * 网络协议枚举，Domain层可独立，便于后续Smb/Ftp/WebDAV扩展。
 */
enum class NetworkProtocol {
    SMB,
    FTP;

    companion object {
        fun fromString(value: String?): NetworkProtocol =
            when (value?.uppercase()) {
                "SMB" -> SMB
                "FTP" -> FTP
                else -> FTP // 默认FTP（安全）
            }
    }
}
