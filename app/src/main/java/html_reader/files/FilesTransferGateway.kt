package com.html_reader.files

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import core.database.entity.NetworkConfigEntity
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

object FilesTransferGateway {
    suspend fun downloadFtpToLocal(
        cacheDir: File,
        config: NetworkConfigEntity,
        remotePath: String,
        displayName: String,
        charset: String
    ): File = withContext(Dispatchers.IO) {
        val targetDir = cacheDir.resolve("ftp_open")
        val target = FilesNetworkGateway.createUniqueCacheFile(targetDir, displayName, "ftp:$remotePath")
        val url = URL(FilesNetworkGateway.buildFtpUrl(config, remotePath, "i", charset))
        url.openStream().use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output, 64 * 1024)
            }
        }
        target
    }

    suspend fun downloadSmbToLocal(
        cacheDir: File,
        config: NetworkConfigEntity,
        remotePath: String,
        displayName: String
    ): File = withContext(Dispatchers.IO) {
        val targetDir = cacheDir.resolve("smb_open")
        val target = FilesNetworkGateway.createUniqueCacheFile(targetDir, displayName, "smb:$remotePath")
        val source = SmbFile(FilesNetworkGateway.buildSmbFileUrl(config, remotePath), FilesSmbGateway.smbContext(config))
        source.inputStream.use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output, 64 * 1024)
            }
        }
        target
    }

    suspend fun uploadToFtp(
        contentResolver: ContentResolver,
        uri: Uri,
        config: NetworkConfigEntity,
        currentPath: String,
        charset: String
    ) = withContext(Dispatchers.IO) {
        val fileName = resolveUploadFileName(contentResolver, uri)
        val targetPath = FilesNetworkGateway.joinFtpPath(currentPath, fileName)
        val targetUrl = URL(FilesNetworkGateway.buildFtpUrl(config, targetPath, "i", charset))
        val connection = targetUrl.openConnection().apply { doOutput = true }
        contentResolver.openInputStream(uri).use { input ->
            if (input == null) {
                error("open input stream failed")
            }
            connection.getOutputStream().use { output ->
                input.copyTo(output, 64 * 1024)
            }
        }
    }

    suspend fun uploadToSmb(
        contentResolver: ContentResolver,
        uri: Uri,
        config: NetworkConfigEntity,
        currentPath: String
    ) = withContext(Dispatchers.IO) {
        val fileName = resolveUploadFileName(contentResolver, uri)
        val targetPath = FilesNetworkGateway.joinSmbPath(currentPath, fileName)
        val target = SmbFile(FilesNetworkGateway.buildSmbFileUrl(config, targetPath), FilesSmbGateway.smbContext(config))
        contentResolver.openInputStream(uri).use { input ->
            if (input == null) {
                error("open input stream failed")
            }
            target.outputStream.use { output ->
                input.copyTo(output, 64 * 1024)
            }
        }
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
