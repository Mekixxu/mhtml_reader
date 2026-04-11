package com.html_reader.files

import java.io.File

data class NavigateUpPlan(
    val handled: Boolean,
    val nextFtpPath: String? = null,
    val nextSmbPath: String? = null,
    val nextLocalDir: File? = null,
    val localAccessDenied: Boolean = false
)

object FilesNavigateUpHelper {
    fun plan(source: BrowseSource, currentDir: File, ftpCurrentPath: String, smbCurrentPath: String): NavigateUpPlan {
        if (source == BrowseSource.FTP) {
            val parent = FilesNetworkGateway.ftpParentPath(ftpCurrentPath)
            if (parent == ftpCurrentPath || (ftpCurrentPath == "/" && parent == "/")) {
                return NavigateUpPlan(handled = false)
            }
            return NavigateUpPlan(handled = true, nextFtpPath = parent)
        }
        if (source == BrowseSource.SMB) {
            val parent = FilesNetworkGateway.smbParentPath(smbCurrentPath)
            if (parent == smbCurrentPath || (smbCurrentPath == "/" && parent == "/")) {
                return NavigateUpPlan(handled = false)
            }
            return NavigateUpPlan(handled = true, nextSmbPath = parent)
        }
        val parent = currentDir.parentFile ?: return NavigateUpPlan(handled = false)
        if (!parent.exists() || !parent.isDirectory) return NavigateUpPlan(handled = false)
        if (parent.listFiles() == null) return NavigateUpPlan(handled = false, localAccessDenied = true)
        return NavigateUpPlan(handled = true, nextLocalDir = parent)
    }
}
