package com.html_reader.backup

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * 备份 JSON 的字节级 I/O 工具。
 *
 * 仅按实际读取字节数限流，不信任 SAF provider 返回的 size；读取完成或失败都会关闭流。
 */
internal object BackupStreamIo {
    /**
     * 提案上限值，待产品确认后才由 [com.html_reader.MoreFragment.maxBackupBytes] 启用；
     * 测试可直接调用 [readUtf8] 注入任意小上限。
     */
    internal const val PROPOSED_MAX_BYTES = 20L * 1024 * 1024
    private const val BUFFER_SIZE = 8 * 1024

    fun readUtf8(input: InputStream, maxBytes: Long): String {
        require(maxBytes > 0L) { "maxBytes must be positive" }
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_SIZE)
            var totalBytes = 0L
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                totalBytes += count
                if (totalBytes > maxBytes) {
                    throw BackupTooLargeException(maxBytes)
                }
                output.write(buffer, 0, count)
            }
            return output.toString(Charsets.UTF_8.name())
        }
    }

    fun writeUtf8(output: OutputStream, text: String) {
        output.use { stream ->
            stream.write(text.toByteArray(Charsets.UTF_8))
            stream.flush()
        }
    }
}

internal class BackupTooLargeException(
    val maxBytes: Long
) : IOException("Backup exceeds $maxBytes bytes")
