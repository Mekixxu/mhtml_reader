package core.common

/**
 * 路径脱敏：移除 URL userInfo 中的密码，仅保留用户名。
 *
 * 例：`ftp://user:secret@host:21/path` -> `ftp://user@host:21/path`
 *
 * 用于收藏入库与备份导出，避免网络凭据明文落库/落盘。
 * 无 userInfo、无密码或无法识别时原样返回。
 */
object UrlCredentialSanitizer {
    private val CREDENTIALS_IN_TEXT = Regex("(://[^:/@\\s]+):[^@\\s]*@")

    /**
     * 从任意文本（如异常 message）中擦除 URL 里的密码，避免日志/错误弹窗泄露凭据。
     */
    fun sanitizeText(text: String): String =
        CREDENTIALS_IN_TEXT.replace(text) { match -> "${match.groupValues[1]}@" }

    fun stripPassword(path: String): String {
        val schemeEnd = path.indexOf("://")
        if (schemeEnd <= 0) return path
        val afterScheme = path.substring(schemeEnd + 3)
        val authorityEnd = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
        val authority = if (authorityEnd < 0) afterScheme else afterScheme.substring(0, authorityEnd)
        val at = authority.lastIndexOf('@')
        if (at < 0) return path
        val userInfo = authority.substring(0, at)
        val colon = userInfo.indexOf(':')
        if (colon < 0) return path
        val user = userInfo.substring(0, colon)
        val hostPort = authority.substring(at + 1)
        if (hostPort.isEmpty()) return path
        val newAuthority = if (user.isEmpty()) hostPort else "$user@$hostPort"
        val rest = if (authorityEnd < 0) "" else afterScheme.substring(authorityEnd)
        return path.substring(0, schemeEnd + 3) + newAuthority + rest
    }
}
