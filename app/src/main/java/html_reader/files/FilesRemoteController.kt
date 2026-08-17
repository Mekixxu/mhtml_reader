package com.html_reader.files

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LifecycleCoroutineScope
import com.html_reader.R
import core.database.entity.NetworkConfigEntity
import core.database.entity.enums.NetworkProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

interface FilesRemoteHost {
    val hostContext: Context
    val hostCacheDir: File
    val hostLifecycleScope: LifecycleCoroutineScope
    fun isAttached(): Boolean
    fun string(resId: Int): String
    fun updateStatus(value: String, isError: Boolean)
    fun updateCurrentDirLabel()
    fun prepareRemoteBrowse(createActionLabelRes: Int)
    fun onRemoteEntriesLoaded(entries: List<BrowserEntry>)
    fun onRemoteEntriesLoadFailed(message: String)
    fun setOperationButtonsEnabled(enabled: Boolean)
    fun showOperationProgress()
    fun hideOperationProgress()
    fun openDownloadedFile(file: File, displayName: String, isBackground: Boolean)
    fun reloadEntriesAfterRemoteMutation()
    fun persistCurrentDir()
    fun formatNetworkError(error: Throwable, protocol: NetworkProtocol): String
    /** 独占互斥：本地文件操作与远程 SMB 操作共用同一把锁，避免并发写进度与列表。 */
    fun tryAcquireOperationLock(): Boolean
    fun releaseOperationLock()
}

/**
 * FTP/SMB 浏览、下载、上传与文件操作的控制器。
 * 从 FilesFragment 拆出，Fragment 只负责本地 UI 状态绑定与事件转发。
 */
class FilesRemoteController(
    private val host: FilesRemoteHost,
    private val supportedExtensions: Set<String>
) {
    var ftpConfig: NetworkConfigEntity? = null
    var smbConfig: NetworkConfigEntity? = null
    var ftpCurrentPath: String = "/"
    var smbCurrentPath: String = "/"
    var ftpResolvedCharset: String? = null
    val ftpDecodeCache: MutableMap<String, String> = ConcurrentHashMap()

    private var loadToken: Long = 0L
    private var remoteLoadJob: Job? = null

    fun cancelRemoteLoad() {
        remoteLoadJob?.cancel()
        loadToken++
    }

    fun syncFromSession(
        ftpConfig: NetworkConfigEntity?,
        ftpResolvedCharset: String?,
        ftpCurrentPath: String,
        smbConfig: NetworkConfigEntity?,
        smbCurrentPath: String
    ) {
        this.ftpConfig = ftpConfig
        this.ftpResolvedCharset = ftpResolvedCharset
        this.ftpCurrentPath = ftpCurrentPath
        this.smbConfig = smbConfig
        this.smbCurrentPath = smbCurrentPath
    }

    fun loadFtpEntries() {
        val config = ftpConfig
        if (config == null) {
            host.updateStatus(host.string(R.string.files_status_invalid_start_path), isError = true)
            return
        }
        host.prepareRemoteBrowse(R.string.action_upload_file)
        if (!isFtpAutoEncoding(config)) {
            ftpResolvedCharset = configuredFtpCharsetName(config)
        }
        ftpDecodeCache.clear()
        loadRemoteEntries(
            config = config,
            protocol = NetworkProtocol.FTP,
            loadingText = host.string(R.string.files_status_ftp_loading)
        ) { fetchFtpEntries(it, ftpCurrentPath) }
    }

    fun loadSmbEntries() {
        val config = smbConfig
        if (config == null) {
            host.updateStatus(host.string(R.string.files_status_invalid_start_path), isError = true)
            return
        }
        host.prepareRemoteBrowse(R.string.action_new_folder)
        loadRemoteEntries(
            config = config,
            protocol = NetworkProtocol.SMB,
            loadingText = host.string(R.string.files_status_smb_loading)
        ) { FilesSmbGateway.fetchEntries(it, smbCurrentPath, supportedExtensions) }
    }

    fun openFtpFile(entry: BrowserEntry, isBackground: Boolean = false) {
        openRemoteFile(
            entry = entry,
            isBackground = isBackground,
            protocol = NetworkProtocol.FTP,
            downloadingStatus = host.string(R.string.files_status_ftp_downloading),
            resolveConfig = { ftpConfig },
            resolveRemotePath = { it.ftpPath },
            downloader = { config, remotePath, displayName ->
                FilesTransferGateway.downloadFtpToLocal(
                    cacheDir = host.hostCacheDir,
                    config = config,
                    remotePath = remotePath,
                    displayName = displayName,
                    charset = ftpEffectiveCharset(config)
                )
            }
        )
    }

    fun openSmbFile(entry: BrowserEntry, isBackground: Boolean = false) {
        openRemoteFile(
            entry = entry,
            isBackground = isBackground,
            protocol = NetworkProtocol.SMB,
            downloadingStatus = host.string(R.string.files_status_smb_downloading),
            resolveConfig = { smbConfig },
            resolveRemotePath = { it.smbPath },
            downloader = { config, remotePath, displayName ->
                FilesTransferGateway.downloadSmbToLocal(
                    cacheDir = host.hostCacheDir,
                    config = config,
                    remotePath = remotePath,
                    displayName = displayName
                )
            }
        )
    }

    fun uploadDocumentToFtp(uri: Uri) {
        uploadRemoteDocument(
            protocol = NetworkProtocol.FTP,
            uploadingStatus = host.string(R.string.files_status_ftp_uploading),
            uploadedStatus = host.string(R.string.files_status_ftp_uploaded),
            resolveConfig = { ftpConfig },
            uploader = { config ->
                FilesTransferGateway.uploadToFtp(
                    contentResolver = host.hostContext.contentResolver,
                    uri = uri,
                    config = config,
                    currentPath = ftpCurrentPath,
                    charset = ftpEffectiveCharset(config)
                )
            }
        )
    }

    fun uploadDocumentToSmb(uri: Uri) {
        uploadRemoteDocument(
            protocol = NetworkProtocol.SMB,
            uploadingStatus = host.string(R.string.files_status_smb_uploading),
            uploadedStatus = host.string(R.string.files_status_smb_uploaded),
            resolveConfig = { smbConfig },
            uploader = { config ->
                FilesTransferGateway.uploadToSmb(
                    contentResolver = host.hostContext.contentResolver,
                    uri = uri,
                    config = config,
                    currentPath = smbCurrentPath
                )
            }
        )
    }

    fun createSmbFolder(name: String) {
        val config = smbConfig ?: return
        runNetworkSmbOperation {
            FilesSmbGateway.createFolder(config, smbCurrentPath, name)
        }
    }

    fun renameSmbEntry(entry: BrowserEntry, newName: String) {
        val config = smbConfig ?: return
        runNetworkSmbOperation {
            FilesSmbGateway.renameEntry(config, entry, newName)
        }
    }

    fun deleteSmbEntry(entry: BrowserEntry) {
        val config = smbConfig ?: return
        runNetworkSmbOperation {
            FilesSmbGateway.deleteEntry(config, entry)
        }
    }

    fun ftpEffectiveCharset(config: NetworkConfigEntity): String =
        configuredFtpCharsetName(config) ?: ftpResolvedCharset ?: "UTF-8"

    fun isFtpAutoEncoding(config: NetworkConfigEntity?): Boolean {
        val encoding = config?.encoding
        return encoding.isNullOrBlank() || encoding.equals("Auto", ignoreCase = true)
    }

    fun configuredFtpCharsetName(config: NetworkConfigEntity?): String? {
        val encoding = config?.encoding?.trim().orEmpty()
        if (encoding.isBlank() || encoding.equals("Auto", ignoreCase = true)) {
            return null
        }
        return encoding
    }

    private fun loadRemoteEntries(
        config: NetworkConfigEntity,
        protocol: NetworkProtocol,
        loadingText: String,
        fetcher: suspend (NetworkConfigEntity) -> List<BrowserEntry>
    ) {
        cancelRemoteLoad()
        host.updateStatus(loadingText, isError = false)
        host.updateCurrentDirLabel()
        val token = ++loadToken
        remoteLoadJob = host.hostLifecycleScope.launch {
            try {
                val entries = fetcher(config)
                if (host.isAttached() && token == loadToken) {
                    host.onRemoteEntriesLoaded(entries)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (error: Throwable) {
                if (host.isAttached() && token == loadToken) {
                    host.onRemoteEntriesLoadFailed(host.formatNetworkError(error, protocol))
                }
            }
        }
    }

    private suspend fun fetchFtpEntries(
        config: NetworkConfigEntity,
        path: String
    ): List<BrowserEntry> = withContext(Dispatchers.IO) {
        val url = URL(FilesNetworkGateway.buildFtpUrl(config, path, "d", ftpEffectiveCharset(config)))
        val lines = url.openStream().bufferedReader(Charsets.ISO_8859_1).use { it.readLines() }
        val parseResult = FilesFtpCodec.parseEntries(
            lines = lines,
            path = path,
            configuredCharset = configuredFtpCharsetName(ftpConfig),
            previousResolvedCharset = ftpResolvedCharset,
            supportedExtensions = supportedExtensions,
            decodeCache = ftpDecodeCache
        )
        ftpResolvedCharset = parseResult.resolvedCharset
        Log.d(
            "FilesRemoteController",
            "ftp_charset_decision charset=$ftpResolvedCharset source=FTP path=$path sample_count=${lines.size}"
        )
        parseResult.entries
    }

    private fun openRemoteFile(
        entry: BrowserEntry,
        isBackground: Boolean,
        protocol: NetworkProtocol,
        downloadingStatus: String,
        resolveConfig: () -> NetworkConfigEntity?,
        resolveRemotePath: (BrowserEntry) -> String?,
        downloader: suspend (NetworkConfigEntity, String, String) -> File
    ) {
        val config = resolveConfig() ?: return
        val remotePath = resolveRemotePath(entry) ?: return
        host.updateStatus(downloadingStatus, isError = false)
        host.hostLifecycleScope.launch {
            runCatching { downloader(config, remotePath, entry.name) }
                .onSuccess { local ->
                    host.updateStatus(host.string(R.string.files_status_done), isError = false)
                    host.openDownloadedFile(local, entry.name, isBackground)
                }
                .onFailure { error ->
                    host.updateStatus(host.formatNetworkError(error, protocol), isError = true)
                }
        }
    }

    private fun uploadRemoteDocument(
        protocol: NetworkProtocol,
        uploadingStatus: String,
        uploadedStatus: String,
        resolveConfig: () -> NetworkConfigEntity?,
        uploader: suspend (NetworkConfigEntity) -> Unit
    ) {
        val config = resolveConfig() ?: return
        host.updateStatus(uploadingStatus, isError = false)
        host.hostLifecycleScope.launch {
            runCatching { uploader(config) }
                .onSuccess {
                    host.updateStatus(uploadedStatus, isError = false)
                    host.reloadEntriesAfterRemoteMutation()
                }
                .onFailure { error ->
                    host.updateStatus(host.formatNetworkError(error, protocol), isError = true)
                }
        }
    }

    private fun runNetworkSmbOperation(block: suspend () -> Unit) {
        if (!host.tryAcquireOperationLock()) return
        host.setOperationButtonsEnabled(false)
        host.showOperationProgress()
        host.hostLifecycleScope.launch {
            try {
                block()
                host.updateStatus(host.string(R.string.files_status_done), isError = false)
            } catch (ce: CancellationException) {
                throw ce
            } catch (error: Throwable) {
                host.updateStatus(host.formatNetworkError(error, NetworkProtocol.SMB), isError = true)
            } finally {
                host.releaseOperationLock()
                host.setOperationButtonsEnabled(true)
                host.hideOperationProgress()
                host.reloadEntriesAfterRemoteMutation()
                host.persistCurrentDir()
            }
        }
    }
}
