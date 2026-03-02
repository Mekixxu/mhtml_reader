package core.title

import core.database.entity.enums.FileType
import core.vfs.model.VfsPath
import java.io.File

/**
 * 仅做路由：由 DI 注入各具体实现，避免隐藏依赖。
 */
class TitleExtractorFacade(
    private val htmlExtractor: TitleExtractor,
    private val pdfExtractor: TitleExtractor
) : TitleExtractor {

    override suspend fun extractTitle(
        source: VfsPath,
        cacheFile: File,
        fileType: FileType,
        maxBytesToRead: Long
    ): String? = when (fileType) {
        FileType.HTML, FileType.MHTML -> htmlExtractor.extractTitle(source, cacheFile, fileType, maxBytesToRead)
        FileType.PDF -> pdfExtractor.extractTitle(source, cacheFile, fileType, maxBytesToRead)
        else -> null
    }
}

