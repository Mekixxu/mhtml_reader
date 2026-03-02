package core.title.impl

import core.common.DispatcherProvider
import core.database.entity.enums.FileType
import core.title.TitleExtractor
import core.vfs.model.VfsPath
import kotlinx.coroutines.withContext
import java.io.File

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
        // TODO: 接入你们选定的 PDF 引擎读取 DocumentInfo.title
        null
    }
}

