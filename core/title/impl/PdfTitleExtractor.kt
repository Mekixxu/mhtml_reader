package core.title.impl

import core.common.DispatcherProvider
import core.database.entity.enums.FileType
import core.title.TitleExtractor
import core.vfs.model.VfsPath
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.text.RegexOption.DOT_MATCHES_ALL

/**
 * PDF 标题提取器（v1.0 骨架）。
 * - 若当前 PDF 引擎无法读取 metadata，则返回 null，UI 继续显示文件名。
 * - 预留接口便于后续增强（metadata / 文本抽取）。
 */
class PdfTitleExtractor(
    private val dispatcherProvider: DispatcherProvider
) : TitleExtractor {

    override suspend fun extractTitle(
        source: VfsPath,
        cacheFile: File,
        fileType: FileType,
        maxBytesToRead: Long
    ): String? = withContext(dispatcherProvider.io) {
        if (fileType != FileType.PDF) return@withContext null
        if (!cacheFile.exists() || !cacheFile.isFile) return@withContext null
        val maxLenLong = minOf(cacheFile.length(), maxBytesToRead.coerceAtLeast(1))
        val maxLen = maxLenLong.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val text = runCatching {
            cacheFile.inputStream().buffered().use { input ->
                val bytes = ByteArray(maxLen)
                val read = input.read(bytes)
                if (read <= 0) {
                    null
                } else {
                    String(bytes, 0, read, StandardCharsets.ISO_8859_1)
                }
            }
        }.getOrNull() ?: return@withContext null
        val regex = Regex("/Title\\s*\\((.*?)\\)", setOf(RegexOption.IGNORE_CASE, DOT_MATCHES_ALL))
        val raw = regex.find(text)?.groupValues?.getOrNull(1) ?: return@withContext null
        decodePdfString(raw).trim().takeIf { it.isNotBlank() }
    }

    private fun decodePdfString(raw: String): String {
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (ch != '\\') {
                out.append(ch)
                i += 1
                continue
            }
            if (i + 1 >= raw.length) {
                break
            }
            val next = raw[i + 1]
            when (next) {
                'n' -> out.append('\n')
                'r' -> out.append('\r')
                't' -> out.append('\t')
                'b' -> out.append('\b')
                'f' -> out.append('\u000C')
                '(' -> out.append('(')
                ')' -> out.append(')')
                '\\' -> out.append('\\')
                else -> {
                    if (next in '0'..'7') {
                        val oct = StringBuilder().append(next)
                        var j = i + 2
                        while (j < raw.length && oct.length < 3 && raw[j] in '0'..'7') {
                            oct.append(raw[j])
                            j += 1
                        }
                        val code = oct.toString().toIntOrNull(8)
                        if (code != null) {
                            out.append(code.toChar())
                        }
                        i = j - 1
                    } else {
                        out.append(next)
                    }
                }
            }
            i += 2
        }
        return out.toString()
    }
}

