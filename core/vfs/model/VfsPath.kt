package core.vfs.model

import android.net.Uri

/**
 * 跨源统一路径模型，后续SMB/FTP/WEB需补全实现
 * - LocalFile: 仅限java.io.File路径
 * - SafTree: Android SAF DocumentFile/Uri路径
 * - Smb/Ftp/Web: v1.0未支持，预留类型[not implemented]
 */
sealed class VfsPath(open val raw: String) {
    data class LocalFile(val filePath: String) : VfsPath(filePath)
    data class SafTree(val uri: Uri) : VfsPath(uri.toString())
    // 预留
    data class Smb(val smbUrl: String) : VfsPath(smbUrl) // [not implemented]
    data class Ftp(val ftpUrl: String) : VfsPath(ftpUrl) // [not implemented]
    data class Web(val httpUrl: String) : VfsPath(httpUrl) // [not implemented]
    fun schemeName(): String = when (this) {
        is LocalFile -> "LOCAL"
        is SafTree -> "SAF"
        is Smb -> "SMB"
        is Ftp -> "FTP"
        is Web -> "WEB"
    }
}
