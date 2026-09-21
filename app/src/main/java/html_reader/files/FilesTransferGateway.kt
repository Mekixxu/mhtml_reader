package com.html_reader.files

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import core.database.entity.NetworkConfigEntity
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import kotlin.system.measureTimeMillis

object FilesTransferGateway {
    private const val TAG = "FilesTransferGateway"
    private const val BUFFER_SIZE = 64 * 1024

    suspend fun downloadFtpToLocal(
        cacheDir: File,
        config: NetworkConfigEntity,
        remotePath: String,
        displayName: String,
        charset: String
    ): File = withContext(Dispatchers.IO) {
        runCatching {
            val targetDir = cacheDir.resolve("ftp_open")
            // 唯一键包含配置身份，避免不同服务器的同路径文件互相覆盖
            val target = FilesNetworkGateway.createUniqueCacheFile(
                targetDir, displayName, "ftp:${config.id}:${config.host}:$remotePath"
            )
            val url = URL(FilesNetworkGateway.buildFtpUrl(config, remotePath, "i", charset))
            val elapsed = measureTimeMillis {
                writeAtomically(target) { output ->
                    url.openStream().use { input -> input.copyTo(output, BUFFER_SIZE) }
                }
            }
            Log.d(TAG, "download_ftp_done host=${config.host} path=$remotePath elapsedMs=$elapsed")
            target
        }.getOrElse { throw mapTransferError(it) }
    }

    suspend fun downloadSmbToLocal(
        cacheDir: File,
        config: NetworkConfigEntity,
        remotePath: String,
        displayName: String
    ): File = withContext(Dispatchers.IO) {
        runCatching {
            val targetDir = cacheDir.resolve("smb_open")
            // 唯一键包含配置身份，避免不同服务器的同路径文件互相覆盖
            val target = FilesNetworkGateway.createUniqueCacheFile(
                targetDir, displayName, "smb:${config.id}:${config.host}:$remotePath"
            )
            val source = SmbFile(FilesNetworkGateway.buildSmbFileUrl(config, remotePath), FilesSmbGateway.smbContext(config))
            val elapsed = measureTimeMillis {
                writeAtomically(target) { output ->
                    source.inputStream.use { input -> input.copyTo(output, BUFFER_SIZE) }
                }
            }
            Log.d(TAG, "download_smb_done host=${config.host} path=$remotePath elapsedMs=$elapsed")
            target
        }.getOrElse { throw mapTransferError(it) }
    }

    suspend fun uploadToFtp(
        contentResolver: ContentResolver,
        uri: Uri,
        config: NetworkConfigEntity,
        currentPath: String,
        charset: String
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = resolveUploadFileName(contentResolver, uri)
            val targetPath = FilesNetworkGateway.joinFtpPath(currentPath, fileName)
            val targetUrl = URL(FilesNetworkGateway.buildFtpUrl(config, targetPath, "i", charset))
            val connection = targetUrl.openConnection().apply { doOutput = true }
            val elapsed = measureTimeMillis {
                contentResolver.openInputStream(uri).use { input ->
                    if (input == null) {
                        throw FilesTransferException.InputUnavailable()
                    }
                    connection.getOutputStream().use { output ->
                        input.copyTo(output, BUFFER_SIZE)
                    }
                }
            }
            Log.d(TAG, "upload_ftp_done host=${config.host} path=$targetPath elapsedMs=$elapsed")
        }.getOrElse { throw mapTransferError(it) }
    }

    suspend fun uploadToSmb(
        contentResolver: ContentResolver,
        uri: Uri,
        config: NetworkConfigEntity,
        currentPath: String
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = resolveUploadFileName(contentResolver, uri)
            val targetPath = FilesNetworkGateway.joinSmbPath(currentPath, fileName)
            val target = SmbFile(FilesNetworkGateway.buildSmbFileUrl(config, targetPath), FilesSmbGateway.smbContext(config))
            val elapsed = measureTimeMillis {
                contentResolver.openInputStream(uri).use { input ->
                    if (input == null) {
                        throw FilesTransferException.InputUnavailable()
                    }
                    target.outputStream.use { output ->
                        input.copyTo(output, BUFFER_SIZE)
                    }
                }
            }
            Log.d(TAG, "upload_smb_done host=${config.host} path=$targetPath elapsedMs=$elapsed")
        }.getOrElse { throw mapTransferError(it) }
    }

    /**
     * 先写随机 .part 临时文件，再原子改名覆盖目标，避免并发/中断产生半截文件。
     */
    private fun writeAtomically(target: File, write: (java.io.OutputStream) -> Unit) {
        val temp = File(target.parentFile, "${target.name}.part-${java.util.UUID.randomUUID()}")
        try {
            FileOutputStream(temp).use(write)
            if (!temp.renameTo(target)) {
                throw java.io.IOException("Atomic rename failed: ${target.name}")
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun mapTransferError(error: Throwable): Throwable {
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
