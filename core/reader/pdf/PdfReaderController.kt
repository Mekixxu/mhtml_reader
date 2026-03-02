package core.reader.pdf

import java.io.File
import kotlinx.coroutines.flow.Flow

/**
 * PDF阅读器控制接口。
 * 必须支持open/goToPage/observePageChanges。
 * 后续可扩展目录/查找/缩放等。
 */
interface PdfReaderController {
    suspend fun open(file: File)
    suspend fun goToPage(pageIndex: Int)
    fun observePageChanges(): Flow<Int>
    // 可扩展：search/zoom/outline等
}
