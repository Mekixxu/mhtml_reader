package core.title.impl

import core.common.DispatcherProvider
import core.database.entity.enums.FileType
import core.title.TitleExtractor
import core.vfs.model.VfsPath
import kotlinx.coroutines.withContext
import java.io.File
import java.util.regex.Pattern

/**
 * 轻量 title 解析器：只读取文件头部一段字节，正则提取 <title>...</title>。
 * v1.0 默认按 UTF-8 解码（多数 MHTML/HTML 由浏览器导出时为 UTF-8；需要更强编码支持可后续增强）。
 */
class HtmlTitleExtractor(
    private val dispatcherProvider: DispatcherProvider
) : TitleExtractor {

    private val titlePattern: Pattern =
        Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)

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

        val headText = try {
            cacheFile.inputStream().buffered().use { input ->
                val buf = ByteArray(maxLen)
                val read = input.read(buf)
                if (read <= 0) return@withContext null
                // v1.0：UTF-8 解码
                String(buf, 0, read, Charsets.UTF_8)
            }
        } catch (_: Exception) {
            return@withContext null
        }

        val matcher = titlePattern.matcher(headText)
        val title = if (matcher.find()) matcher.group(1) else null
        title?.trim()?.takeIf { it.isNotBlank() }
    }
}

