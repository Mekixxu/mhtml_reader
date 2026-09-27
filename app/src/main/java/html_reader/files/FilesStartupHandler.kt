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
        onInvalidStartPath: () -> Unit,
        onCredentialUnavailable: () -> Unit = onInvalidStartPath
    ): InitialOpenState {
        var remainingNetworkConfigId = state.networkConfigId
        var remainingStartPath = state.startPath
        var remainingSafTreeUri = state.safTreeUri

        val networkConfigId = remainingNetworkConfigId
        if (networkConfigId != null) {
            val config = networkConfigRepository.getById(networkConfigId)
            if (config != null && config.decryptFailed) {
                // 凭据解密失败：不建立网络会话，避免把密文当密码发送；直接结束，避免提示被后续路径覆盖
                onCredentialUnavailable()
                return InitialOpenState(networkConfigId = null, startPath = null, safTreeUri = null)
            }
            if (config != null) {
                val sessionName = "${config.protocol.name}: ${config.name}"
                val initialPath = when (config.protocol) {
                    NetworkProtocol.FTP -> FilesNetworkGateway.normalizeFtpPath(config.defaultPath)
                    NetworkProtocol.SMB -> FilesNetworkGateway.normalizeSmbPath(config.defaultPath)
                }
                // 同一网络配置复用既有会话，保留上次浏览位置；无会话才新建
                val existingSessionId = sessionSourceStore.findSessionIdByConfigId(config.id)
                    ?.takeIf { folderSessionRepository.getById(it) != null }
                val sessionId = if (existingSessionId != null) {
                    val existingPath = folderSessionRepository.getById(existingSessionId)?.currentPath.orEmpty()
                    if (existingPath.isBlank()) {
                        folderSessionRepository.updateCurrentDir(existingSessionId, initialPath)
                    }
                    existingSessionId
                } else {
                    folderSessionRepository.add(sessionName, initialPath)
                }
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
                    // 重新进入同一根目录（如再次点击 Home 目录卡片）时恢复上次浏览到的子目录
                    folderSessionRepository.updateCurrentDir(
                        active,
                        resumePathForLocalEntry(active, directory, folderSessionRepository)
                    )
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
                    folderSessionRepository.updateCurrentDir(
                        active,
                        resumePathForLocalEntry(active, directory, folderSessionRepository)
                    )
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

    /**
     * 请求目录是会话当前位置的祖先（或相同）时，保留当前位置，实现「重进卡片恢复上次浏览位置」。
     */
    private suspend fun resumePathForLocalEntry(
        sessionId: Long,
        requestedDir: File,
        folderSessionRepository: FolderSessionRepository
    ): String {
        val existingPath = folderSessionRepository.getById(sessionId)?.currentPath.orEmpty()
        val existing = File(existingPath)
        if (existing.exists() && existing.isDirectory && isSameOrDescendant(existing, requestedDir)) {
            return existing.absolutePath
        }
        return requestedDir.absolutePath
    }

    private fun isSameOrDescendant(candidate: File, ancestor: File): Boolean {
        val candidatePath = runCatching { candidate.canonicalPath }.getOrDefault(candidate.absolutePath)
        val ancestorPath = runCatching { ancestor.canonicalPath }.getOrDefault(ancestor.absolutePath)
            .trimEnd(File.separatorChar)
        return candidatePath == ancestorPath ||
            candidatePath.startsWith(ancestorPath + File.separator)
    }
}
