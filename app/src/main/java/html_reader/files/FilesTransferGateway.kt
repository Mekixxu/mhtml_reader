package com.html_reader.files

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import core.common.EntryNameValidator
import core.database.entity.NetworkConfigEntity
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.coroutines.coroutineContext
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

object FilesTransferGateway {
    private const val TAG = "FilesTransferGateway"
    private const val BUFFER_SIZE = 64 * 1024
    internal const val FTP_CONNECT_TIMEOUT_MS = 10_000
    internal const val FTP_READ_TIMEOUT_MS = 30_000

    /**
     * 下载中转身份键：不同服务器/端口/账号/编码不能共用同一个目标文件。
     * 不包含密码，避免凭据进入文件名或日志。
     */
    internal fun transferIdentityKey(
        protocol: String,
        config: NetworkConfigEntity,
        remotePath: String
    ): String = "$protocol:${config.host}:${config.port}:${config.username}:${config.encoding}:$remotePath"

    suspend fun downloadFtpToLocal(
        cacheDir: File,
        config: NetworkConfigEntity,
        remotePath: String,
        displayName: String,
        charset: String
    ): File = downloadFtpToLocal(
        cacheDir,
        config,
        remotePath,
        displayName,
        charset,
        DefaultFilesTransferResourceFactory
    )

    internal suspend fun downloadFtpToLocal(
        cacheDir: File,
        config: NetworkConfigEntity,
        remotePath: String,
        displayName: String,
        charset: String,
        resourceFactory: FilesTransferResourceFactory
    ): File = withContext(Dispatchers.IO) {
        try {
            val targetDir = cacheDir.resolve("ftp_open")
            val target = FilesNetworkGateway.createUniqueCacheFile(
                targetDir, displayName, transferIdentityKey("ftp", config, remotePath)
            )
            coroutineContext.ensureActive()
            val url = URL(FilesNetworkGateway.buildFtpUrl(config, remotePath, "i", charset))
            val elapsed = measureTimeMillis {
                writeAtomically(target) { output ->
                    resourceFactory.openFtp(
                        url,
                        doOutput = false,
                        connectTimeoutMs = FTP_CONNECT_TIMEOUT_MS,
                        readTimeoutMs = FTP_READ_TIMEOUT_MS
                    ).use { source ->
                        source.inputStream().use { input -> copyCancellable(input, output) }
                    }
                }
            }
            Log.d(TAG, "download_ftp_done host=${config.host} path=$remotePath elapsedMs=$elapsed")
            target
        } catch (t: Throwable) {
            throw mapTransferError(t)
        }
    }

    suspend fun downloadSmbToLocal(
        cacheDir: File,
        config: NetworkConfigEntity,
        remotePath: String,
        displayName: String
    ): File = downloadSmbToLocal(cacheDir, config, remotePath, displayName, DefaultFilesTransferResourceFactory)

    internal suspend fun downloadSmbToLocal(
        cacheDir: File,
        config: NetworkConfigEntity,
        remotePath: String,
        displayName: String,
        resourceFactory: FilesTransferResourceFactory
    ): File = withContext(Dispatchers.IO) {
        try {
            val targetDir = cacheDir.resolve("smb_open")
            val target = FilesNetworkGateway.createUniqueCacheFile(
                targetDir, displayName, transferIdentityKey("smb", config, remotePath)
            )
            coroutineContext.ensureActive()
            val sourceUrl = FilesNetworkGateway.buildSmbFileUrl(config, remotePath)
            val context = FilesSmbGateway.smbContext(config)
            val elapsed = measureTimeMillis {
                writeAtomically(target) { output ->
                    resourceFactory.openSmb(sourceUrl, context).use { source ->
                        source.inputStream().use { input -> copyCancellable(input, output) }
                    }
                }
            }
            Log.d(TAG, "download_smb_done host=${config.host} path=$remotePath elapsedMs=$elapsed")
            target
        } catch (t: Throwable) {
            throw mapTransferError(t)
        }
    }

    internal suspend fun uploadToFtp(
        contentResolver: ContentResolver,
        uri: Uri,
        config: NetworkConfigEntity,
        currentPath: String,
        charset: String,
        attempt: FilesUploadAttempt
    ) = withContext(Dispatchers.IO) {
        try {
            val fileName = resolveUploadFileName(contentResolver, uri)
            val targetPath = FilesNetworkGateway.joinFtpPath(currentPath, fileName)
            val targetUrl = URL(FilesNetworkGateway.buildFtpUrl(config, targetPath, "i", charset))
            uploadFtpFromStream(
                targetUrl = targetUrl,
                inputStream = { contentResolver.openInputStream(uri) },
                config = config,
                targetPath = targetPath,
                attempt = attempt,
                resourceFactory = DefaultFilesTransferResourceFactory
            )
        } catch (t: Throwable) {
            throw mapTransferError(t)
        }
    }

    internal suspend fun uploadFtpFromStream(
        targetUrl: URL,
        inputStream: () -> InputStream?,
        config: NetworkConfigEntity,
        targetPath: String,
        attempt: FilesUploadAttempt,
        resourceFactory: FilesTransferResourceFactory
    ) = withContext(Dispatchers.IO) {
        try {
            coroutineContext.ensureActive()
            attempt.setRemoteTarget("FTP://${config.host}:${config.port}$targetPath")
            val elapsed = measureTimeMillis {
                resourceFactory.openFtp(
                    targetUrl,
                    doOutput = true,
                    connectTimeoutMs = FTP_CONNECT_TIMEOUT_MS,
                    readTimeoutMs = FTP_READ_TIMEOUT_MS
                ).use { target ->
                    coroutineContext.ensureActive()
                    inputStream().use { input ->
                        if (input == null) throw FilesTransferException.InputUnavailable()
                        coroutineContext.ensureActive()
                        attempt.markRemoteWriteMayHaveStarted()
                        coroutineContext.ensureActive()
                        target.outputStream().use { output -> copyCancellable(input, output) }
                    }
                }
            }
            attempt.markCompleted()
            Log.d(TAG, "upload_ftp_done host=${config.host} path=$targetPath elapsedMs=$elapsed")
        } catch (t: Throwable) {
            throw mapUploadError(t, attempt)
        }
    }

    internal suspend fun uploadToSmb(
        contentResolver: ContentResolver,
        uri: Uri,
        config: NetworkConfigEntity,
        currentPath: String,
        attempt: FilesUploadAttempt
    ) = withContext(Dispatchers.IO) {
        try {
            val fileName = resolveUploadFileName(contentResolver, uri)
            val targetPath = FilesNetworkGateway.joinSmbPath(currentPath, fileName)
            uploadSmbFromStream(
                targetUrl = FilesNetworkGateway.buildSmbFileUrl(config, targetPath),
                inputStream = { contentResolver.openInputStream(uri) },
                config = config,
                targetPath = targetPath,
                attempt = attempt,
                resourceFactory = DefaultFilesTransferResourceFactory
            )
        } catch (t: Throwable) {
            throw mapTransferError(t)
        }
    }

    internal suspend fun uploadSmbFromStream(
        targetUrl: String,
        inputStream: () -> InputStream?,
        config: NetworkConfigEntity,
        targetPath: String,
        attempt: FilesUploadAttempt,
        resourceFactory: FilesTransferResourceFactory
    ) = withContext(Dispatchers.IO) {
        try {
            coroutineContext.ensureActive()
            attempt.setRemoteTarget("SMB://${config.host}:${config.port}$targetPath")
            val context = FilesSmbGateway.smbContext(config)
            val elapsed = measureTimeMillis {
                resourceFactory.openSmb(targetUrl, context).use { target ->
                    coroutineContext.ensureActive()
                    inputStream().use { input ->
                        if (input == null) throw FilesTransferException.InputUnavailable()
                        coroutineContext.ensureActive()
                        attempt.markRemoteWriteMayHaveStarted()
                        coroutineContext.ensureActive()
                        target.outputStream().use { output -> copyCancellable(input, output) }
                    }
                }
            }
            attempt.markCompleted()
            Log.d(TAG, "upload_smb_done host=${config.host} path=$targetPath elapsedMs=$elapsed")
        } catch (t: Throwable) {
            throw mapUploadError(t, attempt)
        }
    }

    /**
     * 分块流式拷贝，每个块前后检查协程取消。阻塞中的 read 不会被 ensureActive
     * 强行唤醒；协议 timeout 只约束客户端支持的对应等待，不保证 SAF read 或总上传期限。
     */
    internal suspend fun copyCancellable(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            coroutineContext.ensureActive()
            val count = input.read(buffer)
            coroutineContext.ensureActive()
            if (count < 0) break
            output.write(buffer, 0, count)
        }
    }

    /**
     * 先写随机 .part 临时文件，再检查取消并替换目标；优先原子移动。
     * 改名是提交点；此后取消仍可能让调用方收到 CE，但完整目标已替换。
     */
    internal suspend fun writeAtomically(
        target: File,
        afterCommit: () -> Unit = {},
        write: suspend (OutputStream) -> Unit
    ) {
        val temp = File(target.parentFile, "${target.name}.part-${java.util.UUID.randomUUID()}")
        try {
            FileOutputStream(temp).use { output -> write(output) }
            coroutineContext.ensureActive()
            try {
                Files.move(
                    temp.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            afterCommit()
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    internal fun mapTransferError(error: Throwable): Throwable {
        if (error is CancellationException) {
            return error
        }
        if (error is FilesTransferException) {
            return error
        }
        val message = error.message.orEmpty()
        if (message.contains("530") || message.contains("auth", ignoreCase = true) || message.contains("logon", ignoreCase = true)) {
            return FilesTransferException.AuthFailed(error)
        }
        if (message.contains("access denied", ignoreCase = true) || message.contains("permission", ignoreCase = true)) {
            return FilesTransferException.PermissionDenied(error)
        }
        if (message.contains("timed out", ignoreCase = true) || message.contains("connect", ignoreCase = true) || message.contains("refused", ignoreCase = true)) {
            return FilesTransferException.ConnectionFailed(error)
        }
        return FilesTransferException.Unknown(error)
    }

    private fun mapUploadError(error: Throwable, attempt: FilesUploadAttempt): Throwable {
        if (error is CancellationException || error !is Exception) return error
        if (attempt.remoteWriteMayHaveStarted) {
            return if (error is FilesTransferException.PartialUpload) error
            else FilesTransferException.PartialUpload(mapTransferError(error))
        }
        return mapTransferError(error)
    }

    fun resolveUploadFileName(contentResolver: ContentResolver, uri: Uri): String {
        val fromCursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else {
                    null
                }
            }
        val raw = fromCursor ?: uri.lastPathSegment
        return normalizeUploadFileName(raw)
    }

    internal fun normalizeUploadFileName(raw: String?): String {
        val fallback = "upload_${System.currentTimeMillis()}.bin"
        val candidate = (raw ?: fallback)
            .replace('\\', '_')
            .replace('/', '_')
        return candidate.takeIf(EntryNameValidator::isValid) ?: fallback
    }
}
