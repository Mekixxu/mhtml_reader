package com.html_reader.files

import core.database.entity.enums.NetworkProtocol

object NetworkDisplayHelper {
    fun trimProtocolPrefix(value: String, protocol: NetworkProtocol): String {
        val prefix = when (protocol) {
            NetworkProtocol.FTP -> "ftp://"
            NetworkProtocol.SMB -> "smb://"
        }
        return if (value.startsWith(prefix, ignoreCase = true)) {
            value.substring(prefix.length)
        } else {
            value
        }
    }
}
