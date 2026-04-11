package com.html_reader.files

import android.net.Uri
import com.html_reader.AppCurrentSessionStore
import com.html_reader.AppSessionSourceStore
import core.data.repo.NetworkConfigRepository
import core.database.entity.enums.NetworkProtocol
import core.session.repo.FolderSessionRepository
import java.io.File

data class InitialOpenState(
    val networkConfigId: Long?,
    val startPath: String?,
    val safTreeUri: String?
)

object FilesStartupHandler {
    suspend fun applyInitialOpen(
        state: InitialOpenState,
        requestedNetworkEntry: Boolean,
        networkConfigRepository: NetworkConfigRepository,
        folderSessionRepository: FolderSessionRepository,
        sessionSourceStore: AppSessionSourceStore,
        currentSessionStore: AppCurrentSessionStore,
        onInvalidStartPath: () -> Unit
    ): InitialOpenState {
        var remainingNetworkConfigId = state.networkConfigId
        var remainingStartPath = state.startPath
        var remainingSafTreeUri = state.safTreeUri

        val networkConfigId = remainingNetworkConfigId
        if (networkConfigId != null) {
            val config = networkConfigRepository.getById(networkConfigId)
            if (config != null) {
                val sessionName = "${config.protocol.name}: ${config.name}"
                val initialPath = when (config.protocol) {
                    NetworkProtocol.FTP -> FilesNetworkGateway.normalizeFtpPath(config.defaultPath)
                    NetworkProtocol.SMB -> FilesNetworkGateway.normalizeSmbPath(config.defaultPath)
                }
                val sessionId = folderSessionRepository.add(sessionName, initialPath)
                sessionSourceStore.setNetworkConfigId(sessionId, config.id)
                currentSessionStore.set(sessionId)
            }
            remainingNetworkConfigId = null
        }

        val startPath = remainingStartPath
        if (!startPath.isNullOrBlank()) {
            val directory = File(startPath)
            val active = currentSessionStore.get()
            if (active != null) {
                val hasNetworkBinding = sessionSourceStore.getNetworkConfigId(active) != null
                if (hasNetworkBinding && requestedNetworkEntry) {
                    folderSessionRepository.updateCurrentDir(active, startPath)
                } else if (directory.exists() && directory.isDirectory) {
                    if (hasNetworkBinding) sessionSourceStore.setNetworkConfigId(active, null)
                    folderSessionRepository.updateCurrentDir(active, directory.absolutePath)
                } else if (hasNetworkBinding) {
                    folderSessionRepository.updateCurrentDir(active, startPath)
                } else {
                    onInvalidStartPath()
                }
            }
            remainingStartPath = null
        }

        val treeUriText = remainingSafTreeUri
        if (!treeUriText.isNullOrBlank()) {
            val resolvedPath = FilesPathHelper.resolveSafTreeToLocalPath(Uri.parse(treeUriText))
            val active = currentSessionStore.get()
            if (resolvedPath == null || active == null) {
                onInvalidStartPath()
            } else {
                val directory = File(resolvedPath)
                if (directory.exists() && directory.isDirectory) {
                    if (sessionSourceStore.getNetworkConfigId(active) != null) {
                        sessionSourceStore.setNetworkConfigId(active, null)
                    }
                    folderSessionRepository.updateCurrentDir(active, directory.absolutePath)
                } else {
                    onInvalidStartPath()
                }
            }
            remainingSafTreeUri = null
        }

        return InitialOpenState(
            networkConfigId = remainingNetworkConfigId,
            startPath = remainingStartPath,
            safTreeUri = remainingSafTreeUri
        )
    }
}
