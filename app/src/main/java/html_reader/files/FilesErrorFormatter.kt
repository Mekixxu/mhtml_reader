package com.html_reader.files

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
        return msg.ifBlank { texts.defaultMessage }
    }
}
