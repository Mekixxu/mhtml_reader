package com.html_reader.files

import core.database.entity.NetworkConfigEntity
import core.database.entity.enums.SourceType

object FilesFavoritePathBuilder {
    fun buildPath(
        source: BrowseSource,
        entry: BrowserEntry,
        ftpConfig: NetworkConfigEntity?,
        smbConfig: NetworkConfigEntity?,
        ftpCharsetProvider: (NetworkConfigEntity) -> String
    ): String? {
        return when (source) {
            BrowseSource.LOCAL -> entry.localFile?.absolutePath
            BrowseSource.FTP -> {
                val config = ftpConfig ?: return null
                val remote = entry.ftpPath ?: return null
                val charset = ftpCharsetProvider(config)
                val encodedPath = FilesNetworkGateway.normalizeFtpPath(remote)
                    .split("/")
                    .joinToString("/") { segment ->
                        if (segment.isBlank()) "" else FilesNetworkGateway.encodeSegment(segment, charset)
                    }
                val user = config.username.trim().ifBlank { "anonymous" }
                val encodedUser = FilesNetworkGateway.encodeSegment(user, charset)
                "ftp://$encodedUser@${config.host}:${config.port}$encodedPath"
            }
            BrowseSource.SMB -> {
                val config = smbConfig ?: return null
                val remote = entry.smbPath ?: return null
                "smb://${config.host}:${config.port}${FilesNetworkGateway.normalizeSmbPath(remote)}"
            }
        }
    }

    fun sourceTypeFor(source: BrowseSource): SourceType {
        return when (source) {
            BrowseSource.LOCAL -> SourceType.LOCAL
            BrowseSource.FTP -> SourceType.FTP
            BrowseSource.SMB -> SourceType.SMB
        }
    }
}
