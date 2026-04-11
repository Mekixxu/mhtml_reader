package com.html_reader.files

import core.database.entity.NetworkConfigEntity
import jcifs.CIFSContext
import jcifs.context.SingletonContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

object FilesSmbGateway {
    suspend fun fetchEntries(
        config: NetworkConfigEntity,
        path: String,
        supportedExtensions: Set<String>
    ): List<BrowserEntry> = withContext(Dispatchers.IO) {
        val dir = SmbFile(FilesNetworkGateway.buildSmbDirUrl(config, path), smbContext(config))
        val children = dir.listFiles()?.toList().orEmpty()
        val entries = children.map { child ->
            val resolvedName = child.name.trimEnd('/').ifBlank { child.canonicalPath.substringAfterLast('/').trimEnd('/') }
            val isDirectory = child.isDirectory
            val childPath = FilesNetworkGateway.joinSmbPath(path, resolvedName)
            BrowserEntry(
                localFile = null,
                ftpPath = null,
                smbPath = childPath,
                name = resolvedName,
                isDirectory = isDirectory,
                sizeBytes = if (isDirectory) 0L else child.length(),
                modifiedEpochMs = child.lastModified(),
                modifiedText = null
            )
        }
        val folders = entries.filter { it.isDirectory }.sortedBy { it.name.lowercase(Locale.getDefault()) }
        val files = entries
            .filter { !it.isDirectory && it.name.substringAfterLast('.', "").lowercase(Locale.getDefault()) in supportedExtensions }
            .sortedBy { it.name.lowercase(Locale.getDefault()) }
        folders + files
    }

    suspend fun createFolder(config: NetworkConfigEntity, currentPath: String, name: String) {
        withContext(Dispatchers.IO) {
            val targetPath = FilesNetworkGateway.joinSmbPath(currentPath, name)
            val target = SmbFile(FilesNetworkGateway.buildSmbDirUrl(config, targetPath), smbContext(config))
            if (!target.exists()) {
                target.mkdir()
            }
        }
    }

    suspend fun renameEntry(config: NetworkConfigEntity, entry: BrowserEntry, newName: String) {
        val oldPath = entry.smbPath ?: return
        withContext(Dispatchers.IO) {
            val parent = FilesNetworkGateway.smbParentPath(oldPath)
            val newPath = FilesNetworkGateway.joinSmbPath(parent, newName)
            val from = SmbFile(
                if (entry.isDirectory) FilesNetworkGateway.buildSmbDirUrl(config, oldPath) else FilesNetworkGateway.buildSmbFileUrl(config, oldPath),
                smbContext(config)
            )
            val to = SmbFile(
                if (entry.isDirectory) FilesNetworkGateway.buildSmbDirUrl(config, newPath) else FilesNetworkGateway.buildSmbFileUrl(config, newPath),
                smbContext(config)
            )
            from.renameTo(to)
        }
    }

    suspend fun deleteEntry(config: NetworkConfigEntity, entry: BrowserEntry) {
        val targetPath = entry.smbPath ?: return
        withContext(Dispatchers.IO) {
            val root = SmbFile(
                if (entry.isDirectory) FilesNetworkGateway.buildSmbDirUrl(config, targetPath) else FilesNetworkGateway.buildSmbFileUrl(config, targetPath),
                smbContext(config)
            )
            deleteRecursively(root)
        }
    }

    fun smbContext(config: NetworkConfigEntity): CIFSContext {
        val user = config.username.trim().ifBlank { "guest" }
        val auth = NtlmPasswordAuthenticator("", user, config.password)
        return SingletonContext.getInstance().withCredentials(auth)
    }

    private fun deleteRecursively(target: SmbFile) {
        if (target.isDirectory) {
            target.listFiles()?.forEach { child ->
                deleteRecursively(child)
            }
        }
        target.delete()
    }
}
