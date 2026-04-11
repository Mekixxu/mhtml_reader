package com.html_reader.files

import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

object FilesPathHelper {
    fun resolveSafTreeToLocalPath(uri: Uri): String? {
        return runCatching {
            val treeDocId = DocumentsContract.getTreeDocumentId(uri)
            val split = treeDocId.split(":", limit = 2)
            if (split.size != 2) {
                null
            } else {
                val volume = split[0]
                val relative = split[1].trim('/')
                val base = if (volume.equals("primary", ignoreCase = true)) "/storage/emulated/0" else "/storage/$volume"
                if (relative.isBlank()) base else "$base/$relative"
            }
        }.getOrNull()
    }

    fun buildCurrentDirText(
        source: BrowseSource,
        currentNetworkLabel: String?,
        localDir: File,
        ftpCurrentPath: String,
        smbCurrentPath: String,
        renderer: (label: String, path: String) -> String
    ): String {
        val label = when (source) {
            BrowseSource.FTP -> "[FTP]"
            BrowseSource.SMB -> "[SMB]"
            BrowseSource.LOCAL -> {
                if (currentNetworkLabel == "SD" ||
                    (localDir.absolutePath.startsWith("/storage/") && !localDir.absolutePath.startsWith("/storage/emulated/0"))
                ) "[SD]" else "[LOCAL]"
            }
        }
        val path = when (source) {
            BrowseSource.FTP -> ftpCurrentPath
            BrowseSource.SMB -> smbCurrentPath
            BrowseSource.LOCAL -> localDir.absolutePath
        }
        return renderer(label, path)
    }

    fun pathForPersist(source: BrowseSource, currentDir: File?, ftpCurrentPath: String, smbCurrentPath: String): String? {
        return when (source) {
            BrowseSource.FTP -> ftpCurrentPath
            BrowseSource.SMB -> smbCurrentPath
            BrowseSource.LOCAL -> currentDir?.absolutePath
        }
    }
}
