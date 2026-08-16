package com.html_reader

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 清理 FTP/SMB 下载到 cacheDir/ftp_open 与 cacheDir/smb_open 的临时文件。
 * 这些文件仅在打开远程文件时作为中转，随后由 ReaderTabManager 复制到 app_cache，
 * 长期不清理会持续占用存储空间。
 */
object TransferCacheCleaner {
    suspend fun clean(cacheDir: File, daysUnused: Int) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - daysUnused.coerceAtLeast(1) * 86_400_000L
        listOf("ftp_open", "smb_open").forEach { dirName ->
            val dir = File(cacheDir, dirName)
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < cutoff) {
                    file.delete()
                }
            }
        }
    }
}
