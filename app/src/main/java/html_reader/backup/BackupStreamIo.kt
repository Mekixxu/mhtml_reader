package com.html_reader.backup

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

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

    /**
     * 分块读取并在每次读写前后检查取消。
     * 注意：阻塞中的 InputStream.read 无法被 ensureActive 立即打断；取消能否
     * 及时返回取决于流的超时/关闭能力，不能对所有 SAF provider 承诺即时取消。
     */
    suspend fun readUtf8(input: InputStream, maxBytes: Long): String {
        require(maxBytes > 0L) { "maxBytes must be positive" }
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_SIZE)
            var totalBytes = 0L
            while (true) {
                coroutineContext.ensureActive()
                val count = stream.read(buffer)
                if (count < 0) break
                coroutineContext.ensureActive()
                totalBytes += count
                if (totalBytes > maxBytes) {
                    throw BackupTooLargeException(maxBytes)
                }
                output.write(buffer, 0, count)
            }
            return output.toString(Charsets.UTF_8.name())
        }
    }

    /**
     * 分块写出；当前仍会一次性持有完整 UTF-8 ByteArray，
     * 这是导出格式/内存成本现状，本轮不改变导出格式。
     */
    suspend fun writeUtf8(output: OutputStream, text: String) {
        output.use { stream ->
            val bytes = text.toByteArray(Charsets.UTF_8)
            var offset = 0
            while (offset < bytes.size) {
                coroutineContext.ensureActive()
                val chunkSize = minOf(BUFFER_SIZE, bytes.size - offset)
                stream.write(bytes, offset, chunkSize)
                offset += chunkSize
            }
            stream.flush()
        }
    }
}

internal class BackupTooLargeException(
    val maxBytes: Long
) : IOException("Backup exceeds $maxBytes bytes")
