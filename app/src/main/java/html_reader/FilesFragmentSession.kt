package com.html_reader

import androidx.lifecycle.lifecycleScope
import com.html_reader.files.FilesSessionPlanner
import com.html_reader.files.FilesStartupHandler
import com.html_reader.files.InitialOpenState
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal fun FilesFragment.restoreSessionAndLoad() {
        viewLifecycleOwner.lifecycleScope.launch {
            ensureDefaultSession()
            val updated = FilesStartupHandler.applyInitialOpen(
                state = InitialOpenState(
                    networkConfigId = initialNetworkConfigId,
                    startPath = initialStartPath,
                    safTreeUri = initialSafTreeUri
                ),
                requestedNetworkEntry = requestedNetworkEntry,
                networkConfigRepository = networkConfigRepository,
                folderSessionRepository = folderSessionRepository,
                sessionSourceStore = sessionSourceStore,
                currentSessionStore = currentSessionStore,
                onInvalidStartPath = {
                    updateStatus(getString(R.string.files_status_invalid_start_path), isError = true)
                },
                onCredentialUnavailable = {
                    updateStatus(getString(R.string.files_status_network_credential_invalid), isError = true)
                }
            )
            initialNetworkConfigId = updated.networkConfigId
            initialStartPath = updated.startPath
            initialSafTreeUri = updated.safTreeUri
            val active = currentSessionStore.get()
            if (active != null) switchToSession(active) else loadEntries()
            // 初始恢复完成后才开始监听切换：observe 是 StateFlow，立即发射当前值，
            // 若与 applyInitialOpen 并发会双次加载并用旧位置覆盖新位置
            observeSessionSwitch()
        }
    }

internal fun FilesFragment.observeSessionSwitch() {
        viewLifecycleOwner.lifecycleScope.launch {
            currentSessionStore.observe().collect { sessionId ->
                if (sessionId != null && sessionId != currentSessionId) {
                    switchToSession(sessionId)
                }
            }
        }
    }


internal suspend fun FilesFragment.ensureDefaultSession() {
        val sessions = folderSessionRepository.getAll()
        if (sessions.isEmpty()) {
            val root = defaultRootDir().absolutePath
            val id = folderSessionRepository.add("Default", root)
            currentSessionStore.set(id)
            return
        }
        if (currentSessionStore.get() == null) {
            currentSessionStore.set(sessions.first().id)
        }
    }


internal suspend fun FilesFragment.switchToSession(sessionId: Long) {
        // 切出前保存当前会话的列表滚动位置（会话 id 与路径仍是切出方状态）
        saveScrollState()
        val session = folderSessionRepository.getById(sessionId) ?: return
        currentSessionId = sessionId
        currentSessionStore.set(sessionId)
        val linkedNetworkConfig = sessionSourceStore.getNetworkConfigId(sessionId)
            ?.let { networkConfigRepository.getById(it) }
        // 解密失败的凭据是密文，禁止发往远端；提示用户重设密码并清空残留列表
        if (linkedNetworkConfig?.decryptFailed == true) {
            updateStatus(getString(R.string.files_status_network_credential_invalid), isError = true)
            allEntries.clear()
            displayedEntries.clear()
            adapter.notifyDataSetChanged()
            return
        }
        val plan = FilesSessionPlanner.build(
            session = session,
            linkedNetworkConfig = linkedNetworkConfig,
            defaultRootDir = defaultRootDir(),
            configuredFtpCharsetName = { remoteController.configuredFtpCharsetName(it) }
        )
        if (sortSpinner.selectedItemPosition != plan.sortIndex) {
            sortSpinner.setSelection(plan.sortIndex, false)
        }
        currentNetworkLabel = plan.currentNetworkLabel
        browseSource = plan.source
        remoteController.syncFromSession(
            ftpConfig = plan.ftpConfig,
            ftpResolvedCharset = plan.ftpResolvedCharset,
            ftpCurrentPath = plan.ftpCurrentPath,
            smbConfig = plan.smbConfig,
            smbCurrentPath = plan.smbCurrentPath
        )
        currentDir = plan.localDir
        selectedEntry = null
        loadEntries()
    }


