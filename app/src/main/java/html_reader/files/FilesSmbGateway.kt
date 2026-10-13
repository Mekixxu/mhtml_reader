package com.html_reader.files

import core.database.entity.NetworkConfigEntity
import jcifs.CIFSContext
import jcifs.CIFSException
import jcifs.context.SingletonContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import java.util.Locale
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FilesSmbGateway {
    internal const val SMB_CONNECT_TIMEOUT_MS = 10_000
    internal const val SMB_SOCKET_TIMEOUT_MS = 30_000
    internal const val SMB_RESPONSE_TIMEOUT_MS = 30_000

    private val baseContext: CIFSContext by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val properties = Properties().apply {
            setProperty("jcifs.smb.client.connTimeout", SMB_CONNECT_TIMEOUT_MS.toString())
            setProperty("jcifs.smb.client.soTimeout", SMB_SOCKET_TIMEOUT_MS.toString())
            setProperty("jcifs.smb.client.responseTimeout", SMB_RESPONSE_TIMEOUT_MS.toString())
        }
        try {
            SingletonContext.init(properties)
        } catch (initializationError: CIFSException) {
            val existing = SingletonContext.getInstance()
            check(existing.hasTransferTimeouts()) {
                "SMB context was initialized before transfer timeouts were configured"
            }
            return@lazy existing
        }
        SingletonContext.getInstance().also { context ->
            check(context.hasTransferTimeouts()) { "SMB transfer timeout configuration was not applied" }
        }
    }

    suspend fun fetchEntries(
        config: NetworkConfigEntity,
        path: String,
        supportedExtensions: Set<String>
    ): List<BrowserEntry> = withContext(Dispatchers.IO) {
        val entries = SmbFile(FilesNetworkGateway.buildSmbDirUrl(config, path), smbContext(config)).use { dir ->
            val children = dir.listFiles().orEmpty()
            try {
                children.map { child ->
                    val resolvedName = child.name.trimEnd('/')
                        .ifBlank { child.canonicalPath.substringAfterLast('/').trimEnd('/') }
                    val isDirectory = child.isDirectory
                    BrowserEntry(
                        localFile = null,
                        ftpPath = null,
                        smbPath = FilesNetworkGateway.joinSmbPath(path, resolvedName),
                        name = resolvedName,
                        isDirectory = isDirectory,
                        sizeBytes = if (isDirectory) 0L else child.length(),
                        modifiedEpochMs = child.lastModified(),
                        modifiedText = null
                    )
                }
            } finally {
                children.forEach { it.close() }
            }
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
            SmbFile(FilesNetworkGateway.buildSmbDirUrl(config, targetPath), smbContext(config)).use { target ->
                if (!target.exists()) target.mkdir()
            }
        }
    }

    suspend fun renameEntry(config: NetworkConfigEntity, entry: BrowserEntry, newName: String) {
        val oldPath = entry.smbPath ?: return
        withContext(Dispatchers.IO) {
            val parent = FilesNetworkGateway.smbParentPath(oldPath)
            val newPath = FilesNetworkGateway.joinSmbPath(parent, newName)
            SmbFile(
                if (entry.isDirectory) FilesNetworkGateway.buildSmbDirUrl(config, oldPath) else FilesNetworkGateway.buildSmbFileUrl(config, oldPath),
                smbContext(config)
            ).use { from ->
                SmbFile(
                    if (entry.isDirectory) FilesNetworkGateway.buildSmbDirUrl(config, newPath) else FilesNetworkGateway.buildSmbFileUrl(config, newPath),
                    smbContext(config)
                ).use { to ->
                    if (to.exists()) {
                        throw FilesTransferException.Unknown(IllegalStateException("Target already exists"))
                    }
                    from.renameTo(to)
                }
            }
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
        return baseContext.withCredentials(auth)
    }

    private fun deleteRecursively(target: SmbFile) {
        target.use { resource ->
            if (resource.isDirectory) {
                val children = resource.listFiles().orEmpty()
                var nextChild = 0
                try {
                    while (nextChild < children.size) {
                        deleteRecursively(children[nextChild++])
                    }
                } finally {
                    while (nextChild < children.size) {
                        children[nextChild++].close()
                    }
                }
            }
            resource.delete()
        }
    }

    private fun CIFSContext.hasTransferTimeouts(): Boolean =
        config.connTimeout == SMB_CONNECT_TIMEOUT_MS &&
            config.soTimeout == SMB_SOCKET_TIMEOUT_MS &&
            config.responseTimeout == SMB_RESPONSE_TIMEOUT_MS
}
