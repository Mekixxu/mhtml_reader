package com.html_reader.files

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import core.database.entity.NetworkConfigEntity
import jcifs.smb.SmbFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
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
    internal const val SMB_CONNECT_TIMEOUT_MS = 10_000
    internal const val SMB_READ_TIMEOUT_MS = 30_000

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
    ): File = withContext(Dispatchers.IO) {
        try {
            val targetDir = cacheDir.resolve("ftp_open")
            // 没有可信远端版本，禁止按长度复用；每次走原子下载。
            val target = FilesNetworkGateway.createUniqueCacheFile(
                targetDir, displayName, transferIdentityKey("ftp", config, remotePath)
            )
            val connection = URL(FilesNetworkGateway.buildFtpUrl(config, remotePath, "i", charset))
                .openConnection()
                .apply {
                    connectTimeout = FTP_CONNECT_TIMEOUT_MS
                    readTimeout = FTP_READ_TIMEOUT_MS
                }
            val elapsed = measureTimeMillis {
                writeAtomically(target) { output ->
                    connection.getInputStream().use { input ->
                        copyCancellable(input, output)
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
    ): File = withContext(Dispatchers.IO) {
        try {
            val targetDir = cacheDir.resolve("smb_open")
            // 没有可信远端版本，禁止按长度复用；每次走原子下载。
            val target = FilesNetworkGateway.createUniqueCacheFile(
                targetDir, displayName, transferIdentityKey("smb", config, remotePath)
            )
            val source = SmbFile(FilesNetworkGateway.buildSmbFileUrl(config, remotePath), FilesSmbGateway.smbContext(config))
                .apply {
                    connectTimeout = SMB_CONNECT_TIMEOUT_MS
                    readTimeout = SMB_READ_TIMEOUT_MS
                }
            val elapsed = measureTimeMillis {
                writeAtomically(target) { output ->
                    source.inputStream.use { input ->
                        copyCancellable(input, output)
                    }
                }
            }
            Log.d(TAG, "download_smb_done host=${config.host} path=$remotePath elapsedMs=$elapsed")
            target
        } catch (t: Throwable) {
            throw mapTransferError(t)
        }
    }

    suspend fun uploadToFtp(
        contentResolver: ContentResolver,
        uri: Uri,
        config: NetworkConfigEntity,
        currentPath: String,
        charset: String
    ) = withContext(Dispatchers.IO) {
        try {
            val fileName = resolveUploadFileName(contentResolver, uri)
            val targetPath = FilesNetworkGateway.joinFtpPath(currentPath, fileName)
            val targetUrl = URL(FilesNetworkGateway.buildFtpUrl(config, targetPath, "i", charset))
            val connection = targetUrl.openConnection().apply {
                doOutput = true
                connectTimeout = FTP_CONNECT_TIMEOUT_MS
                readTimeout = FTP_READ_TIMEOUT_MS
            }
            val elapsed = measureTimeMillis {
                contentResolver.openInputStream(uri).use { input ->
                    if (input == null) {
                        throw FilesTransferException.InputUnavailable()
                    }
                    connection.getOutputStream().use { output ->
                        copyCancellable(input, output)
                    }
                }
            }
            Log.d(TAG, "upload_ftp_done host=${config.host} path=$targetPath elapsedMs=$elapsed")
        } catch (t: Throwable) {
            throw mapTransferError(t)
        }
    }

    suspend fun uploadToSmb(
        contentResolver: ContentResolver,
        uri: Uri,
        config: NetworkConfigEntity,
        currentPath: String
    ) = withContext(Dispatchers.IO) {
        try {
            val fileName = resolveUploadFileName(contentResolver, uri)
            val targetPath = FilesNetworkGateway.joinSmbPath(currentPath, fileName)
            val target = SmbFile(FilesNetworkGateway.buildSmbFileUrl(config, targetPath), FilesSmbGateway.smbContext(config))
                .apply {
                    connectTimeout = SMB_CONNECT_TIMEOUT_MS
                    readTimeout = SMB_READ_TIMEOUT_MS
                }
            val elapsed = measureTimeMillis {
                contentResolver.openInputStream(uri).use { input ->
                    if (input == null) {
                        throw FilesTransferException.InputUnavailable()
                    }
                    target.outputStream.use { output ->
                        copyCancellable(input, output)
                    }
                }
            }
            Log.d(TAG, "upload_smb_done host=${config.host} path=$targetPath elapsedMs=$elapsed")
        } catch (t: Throwable) {
            throw mapTransferError(t)
        }
    }

    /**
     * 分块流式拷贝，每个块前后检查协程取消。阻塞中的 read 不会被 ensureActive
     * 强行唤醒，但会由 FTP/SMB 的超时与流关闭负责退出。
     */
    internal suspend fun copyCancellable(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            coroutineContext.ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            coroutineContext.ensureActive()
            output.write(buffer, 0, count)
        }
    }

    /**
     * 先写随机 .part 临时文件，再检查取消并原子改名覆盖目标。
     * 失败/取消时 finally 删除临时文件，不改动已有目标。
     */
    internal suspend fun writeAtomically(target: File, write: suspend (OutputStream) -> Unit) {
        val temp = File(target.parentFile, "${target.name}.part-${java.util.UUID.randomUUID()}")
        try {
            FileOutputStream(temp).use { output -> write(output) }
            coroutineContext.ensureActive()
            if (!temp.renameTo(target)) {
                throw IOException("Atomic rename failed: ${target.name}")
            }
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
        val raw = fromCursor ?: uri.lastPathSegment ?: "upload_${System.currentTimeMillis()}.bin"
        return raw.replace("/", "_").ifBlank { "upload_${System.currentTimeMillis()}.bin" }
    }
}
