package com.html_reader.files

import core.database.entity.enums.NetworkProtocol

object NetworkDisplayHelper {
    private val URL_PREFIX = Regex("^(ftp|smb)://", RegexOption.IGNORE_CASE)
    private val SESSION_PREFIX = Regex("^(FTP|SMB):\\s*(.*)$", RegexOption.IGNORE_CASE)

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

    /**
     * 配置名是否有展示意义：空白、剥掉协议前缀后为空、或与协议名相同
     * （如 FTP 配置名就叫 ftp）均视为无，避免列表出现「FTP: ftp」这类冗余。
     */
    fun meaningfulName(name: String, protocol: NetworkProtocol): String? =
        trimProtocolPrefix(name.trim(), protocol)
            .ifBlank { null }
            ?.takeUnless { it.equals(protocol.name, ignoreCase = true) }

    /**
     * 网络会话名展示：剥掉「协议: 」前缀与裸 URL 前缀（如 FTP: ftp://host），
     * 余下无意义（空或与协议名重复）时只显示协议名。
     */
    fun displaySessionName(name: String): String {
        val match = SESSION_PREFIX.matchEntire(name.trim()) ?: return name
        val protocolLabel = match.groupValues[1].uppercase()
        val rest = match.groupValues[2].replaceFirst(URL_PREFIX, "").trim()
        val meaningful = rest.ifBlank { null }?.takeUnless { it.equals(protocolLabel, ignoreCase = true) }
        return if (meaningful == null) protocolLabel else "$protocolLabel: $meaningful"
    }
}
