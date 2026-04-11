package com.html_reader.files

import java.io.File

data class BrowseState(
    val source: BrowseSource = BrowseSource.LOCAL,
    val localDir: File? = null,
    val ftpPath: String = "/",
    val smbPath: String = "/"
)

class FilesBrowseCoordinator(initial: BrowseState = BrowseState()) {
    private var state: BrowseState = initial

    fun current(): BrowseState = state

    fun switchToLocal(dir: File?): BrowseState {
        state = state.copy(source = BrowseSource.LOCAL, localDir = dir)
        return state
    }

    fun switchToFtp(path: String): BrowseState {
        state = state.copy(source = BrowseSource.FTP, ftpPath = FilesNetworkGateway.normalizeFtpPath(path))
        return state
    }

    fun switchToSmb(path: String): BrowseState {
        state = state.copy(source = BrowseSource.SMB, smbPath = FilesNetworkGateway.normalizeSmbPath(path))
        return state
    }

    fun enterDirectory(entry: BrowserEntry): BrowseState {
        if (!entry.isDirectory) return state
        state = when (state.source) {
            BrowseSource.LOCAL -> state.copy(localDir = entry.localFile ?: state.localDir)
            BrowseSource.FTP -> state.copy(ftpPath = entry.ftpPath ?: state.ftpPath)
            BrowseSource.SMB -> state.copy(smbPath = entry.smbPath ?: state.smbPath)
        }
        return state
    }
}
