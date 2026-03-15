package core.title.impl

import core.common.DispatcherProvider
import core.database.entity.enums.FileType
import core.title.TitleExtractor
import core.vfs.model.VfsPath
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/**
 * 轻量 title 解析器：读取文件头部字节，检测编码并解码，正则提取 <title>...</title>。
 * 支持自动检测 charset，优先尝试 UTF-8，失败尝试 GBK，最后回退到 ISO-8859-1。
 */
class HtmlTitleExtractor(
    private val dispatcherProvider: DispatcherProvider
) : TitleExtractor {

    private val titlePattern: Pattern =
        Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)

    // Regex to find charset in meta tags or headers
    // Matches: charset="gb2312" or charset=gb2312 or encoding="..."
    private val charsetPattern: Pattern =
        Pattern.compile("(?:charset|encoding)=[\"']?([-\\w]+)[\"']?", Pattern.CASE_INSENSITIVE)

    override suspend fun extractTitle(
        source: VfsPath,
        cacheFile: File,
        fileType: FileType,
        maxBytesToRead: Long
    ): String? = withContext(dispatcherProvider.io) {
        if (fileType != FileType.HTML && fileType != FileType.MHTML) return@withContext null
        if (!cacheFile.exists() || !cacheFile.isFile) return@withContext null

        val maxLenLong = minOf(cacheFile.length(), maxBytesToRead.coerceAtLeast(1))
        val maxLen = maxLenLong.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        val bytes = try {
            cacheFile.inputStream().buffered().use { input ->
                val buf = ByteArray(maxLen)
                val read = input.read(buf)
                if (read <= 0) return@withContext null
                if (read < maxLen) buf.copyOf(read) else buf
            }
        } catch (_: Exception) {
            return@withContext null
        }

        // 1. Detect Charset
        val detectedCharset = detectCharset(bytes)

        // 2. Decode Content
        val (decodedText, usedCharset) = decodeContent(bytes, detectedCharset)

        // 3. Extract Title
        val matcher = titlePattern.matcher(decodedText)
        val title = if (matcher.find()) matcher.group(1) else null

        // 4. Decode Quoted-Printable (using the same charset if needed)
        val rawTitle = title?.trim()?.takeIf { it.isNotBlank() } ?: return@withContext null
        decodeQuotedPrintable(rawTitle, usedCharset)
    }

    private fun detectCharset(bytes: ByteArray): Charset? {
        // Scan first 4KB or less
        val scanLen = minOf(bytes.size, 4096)
        // Use ISO-8859-1 to safely map bytes to chars 1-to-1 for regex matching
        val header = String(bytes, 0, scanLen, StandardCharsets.ISO_8859_1)

        val matcher = charsetPattern.matcher(header)
        if (matcher.find()) {
            val charsetName = matcher.group(1)
            return try {
                resolveCharset(charsetName)
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    private fun resolveCharset(name: String): Charset {
        // Handle common aliases
        if (name.equals("gb2312", ignoreCase = true)) {
            return Charset.forName("GBK")
        }
        return Charset.forName(name)
    }

    private fun decodeContent(bytes: ByteArray, detectedCharset: Charset?): Pair<String, Charset> {
        // Strategy:
        // 1. Detected Charset (if any)
        // 2. UTF-8
        // 3. GBK
        // 4. ISO-8859-1 (fallback)

        if (detectedCharset != null) {
            try {
                val text = decodeWithCharset(bytes, detectedCharset)
                return text to detectedCharset
            } catch (e: Exception) {
                // Detected charset failed, fall through
            }
        }

        // Try UTF-8
        try {
            val text = decodeWithCharset(bytes, StandardCharsets.UTF_8)
            return text to StandardCharsets.UTF_8
        } catch (e: Exception) {
            // UTF-8 failed
        }

        // Try GBK
        try {
            val gbk = Charset.forName("GBK")
            val text = decodeWithCharset(bytes, gbk)
            return text to gbk
        } catch (e: Exception) {
            // GBK failed
        }

        // Fallback to ISO-8859-1
        return String(bytes, StandardCharsets.ISO_8859_1) to StandardCharsets.ISO_8859_1
    }

    private fun decodeWithCharset(bytes: ByteArray, charset: Charset): String {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
    }

    private fun decodeQuotedPrintable(input: String, charset: Charset): String {
        // 1. Remove soft line breaks (=\r\n, =\n, =\r)
        val cleaned = input.replace(Regex("=\r\n|=\r|=\n"), "")

        // Simple check if it looks like Quoted-Printable (e.g., =E5=92...)
        if (!cleaned.contains("=")) return cleaned

        try {
            val bytes = java.io.ByteArrayOutputStream()
            var i = 0
            val len = cleaned.length
            while (i < len) {
                val c = cleaned[i]
                if (c == '=') {
                    if (i + 2 < len) {
                        val hex = cleaned.substring(i + 1, i + 3)
                        try {
                            val b = hex.toInt(16)
                            bytes.write(b)
                            i += 3
                        } catch (e: NumberFormatException) {
                            // Not a valid hex code, just treat as char
                            bytes.write('='.code)
                            i++
                        }
                    } else {
                        bytes.write('='.code)
                        i++
                    }
                } else {
                    bytes.write(c.code)
                    i++
                }
            }
            
            val byteArray = bytes.toByteArray()
            
            // If the charset is UTF-8, we try strict decoding.
            // If it fails, we try GBK as a common fallback for QP content.
            if (charset == StandardCharsets.UTF_8) {
                try {
                    return decodeWithCharset(byteArray, StandardCharsets.UTF_8)
                } catch (e: Exception) {
                    // UTF-8 failed, try GBK
                    try {
                        return decodeWithCharset(byteArray, Charset.forName("GBK"))
                    } catch (e2: Exception) {
                        // GBK failed, fall back to loose UTF-8
                    }
                }
            }
            
            return String(byteArray, charset)
        } catch (e: Exception) {
            return input
        }
    }
}
