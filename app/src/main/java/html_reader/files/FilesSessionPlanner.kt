package com.html_reader.files

import core.database.entity.NetworkConfigEntity
import core.database.entity.enums.NetworkProtocol
import core.session.entity.FolderSessionEntity
import java.io.File

data class SessionApplyPlan(
    val sortIndex: Int,
    val currentNetworkLabel: String?,
    val source: BrowseSource,
    val ftpConfig: NetworkConfigEntity? = null,
    val ftpResolvedCharset: String? = null,
    val ftpCurrentPath: String = "/",
    val smbConfig: NetworkConfigEntity? = null,
    val smbCurrentPath: String = "/",
    val localDir: File? = null
)

object FilesSessionPlanner {
    fun build(
        session: FolderSessionEntity,
        linkedNetworkConfig: NetworkConfigEntity?,
        defaultRootDir: File,
        configuredFtpCharsetName: (NetworkConfigEntity?) -> String?
    ): SessionApplyPlan {
        val sortIndex = session.sortOption.coerceIn(0, 4)
        val label = linkedNetworkConfig?.let { "${it.protocol.name}://${it.host}" }
        if (linkedNetworkConfig?.protocol == NetworkProtocol.FTP) {
            return SessionApplyPlan(
                sortIndex = sortIndex,
                currentNetworkLabel = label,
                source = BrowseSource.FTP,
                ftpConfig = linkedNetworkConfig,
                ftpResolvedCharset = configuredFtpCharsetName(linkedNetworkConfig),
                ftpCurrentPath = FilesNetworkGateway.normalizeFtpPath(
                    session.currentPath.ifBlank { linkedNetworkConfig.defaultPath }
                )
            )
        }
        if (linkedNetworkConfig?.protocol == NetworkProtocol.SMB) {
            return SessionApplyPlan(
                sortIndex = sortIndex,
                currentNetworkLabel = label,
                source = BrowseSource.SMB,
                smbConfig = linkedNetworkConfig,
                smbCurrentPath = FilesNetworkGateway.normalizeSmbPath(
                    session.currentPath.ifBlank { linkedNetworkConfig.defaultPath }
                )
            )
        }
        val preferred = File(session.currentPath)
        val localDir = if (preferred.exists() && preferred.isDirectory) {
            preferred
        } else {
            File(session.rootPath).takeIf { it.exists() && it.isDirectory } ?: defaultRootDir
        }
        return SessionApplyPlan(
            sortIndex = sortIndex,
            currentNetworkLabel = label,
            source = BrowseSource.LOCAL,
            localDir = localDir
        )
    }
}
