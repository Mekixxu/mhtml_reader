package com.html_reader.files

import core.common.UrlCredentialSanitizer
import core.database.entity.enums.NetworkProtocol

data class NetworkErrorTexts(
    val ftpAuthFailed: String,
    val smbAuthFailed: String,
    val ftpConnectionFailed: String,
    val smbConnectionFailed: String,
    val defaultMessage: String
)

object FilesErrorFormatter {
    fun format(error: Throwable, protocol: NetworkProtocol, texts: NetworkErrorTexts): String {
        if (error is FilesTransferException.AuthFailed) {
            return if (protocol == NetworkProtocol.FTP) texts.ftpAuthFailed else texts.smbAuthFailed
        }
        if (error is FilesTransferException.ConnectionFailed) {
            return if (protocol == NetworkProtocol.FTP) texts.ftpConnectionFailed else texts.smbConnectionFailed
        }
        if (error is FilesTransferException.InputUnavailable) {
            return "Selected document is not readable"
        }
        if (error is FilesTransferException.PermissionDenied) {
            return "Permission denied while accessing remote target"
        }
        val msg = error.message.orEmpty()
        if (protocol == NetworkProtocol.FTP && msg.contains("530")) {
            return texts.ftpAuthFailed
        }
        if (protocol == NetworkProtocol.SMB &&
            (msg.contains("logon failure", ignoreCase = true) || msg.contains("access denied", ignoreCase = true))
        ) {
            return texts.smbAuthFailed
        }
        if (msg.contains("timed out", ignoreCase = true) || msg.contains("connect", ignoreCase = true)) {
            return if (protocol == NetworkProtocol.FTP) texts.ftpConnectionFailed else texts.smbConnectionFailed
        }
        // 兜底：擦除异常消息中可能携带的 URL 密码
        return UrlCredentialSanitizer.sanitizeText(msg).ifBlank { texts.defaultMessage }
    }
}
