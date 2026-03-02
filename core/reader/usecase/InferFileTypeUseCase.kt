package core.reader.usecase

import core.database.entity.enums.FileType

/**
 * 文件类型推断。支持filename/ext/url。
 */
object InferFileTypeUseCase {
    fun infer(fileNameOrUrl: String): FileType = when {
        fileNameOrUrl.endsWith(".pdf", true) -> FileType.PDF
        fileNameOrUrl.endsWith(".mhtml", true) || fileNameOrUrl.endsWith(".mht", true) -> FileType.MHTML
        fileNameOrUrl.endsWith(".html", true) || fileNameOrUrl.endsWith(".htm", true) -> FileType.HTML
        fileNameOrUrl.startsWith("http://") || fileNameOrUrl.startsWith("https://") -> FileType.WEB
        else -> FileType.WEB
    }
}
