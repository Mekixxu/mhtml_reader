package com.html_reader

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 清理 FTP/SMB 下载到 cacheDir/ftp_open 与 cacheDir/smb_open 的临时文件。
 * 这些文件仅在打开远程文件时作为中转，随后由 ReaderTabManager 复制到 app_cache，
 * 长期不清理会持续占用存储空间。
 *
 * `.part-*` 原子写中断残留按小时级阈值清理：清理任务运行时可能仍有传输在写
 * （写入期间 mtime 持续更新），只删除超过阈值未被写入的残留。
 */
object TransferCacheCleaner {
    private const val PART_SUFFIX = ".part-"
    private const val PART_STALE_MS = 60L * 60 * 1000

    suspend fun clean(cacheDir: File, daysUnused: Int) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - daysUnused.coerceAtLeast(1) * 86_400_000L
        val partCutoff = System.currentTimeMillis() - PART_STALE_MS
        listOf("ftp_open", "smb_open").forEach { dirName ->
            val dir = File(cacheDir, dirName)
            dir.listFiles()?.forEach { file ->
                if (!file.isFile) return@forEach
                val stalePart = file.name.contains(PART_SUFFIX) && file.lastModified() < partCutoff
                if (stalePart || file.lastModified() < cutoff) {
                    file.delete()
                }
            }
        }
    }
}
