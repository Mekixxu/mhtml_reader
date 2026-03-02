package core.reader.pdf.impl

import core.reader.pdf.PdfReaderController
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow

/**
 * v1.0 PDF阅读器Stub（接口实现，后续用PDF库替换）
 * 支持页码变化事件。
 */
class PdfReaderControllerStub : PdfReaderController {
    private val pageState = MutableStateFlow(0)
    override suspend fun open(file: File) {
        pageState.value = 0 // 假定第一页
    }
    override suspend fun goToPage(pageIndex: Int) {
        pageState.value = pageIndex
    }
    override fun observePageChanges(): Flow<Int> = pageState
}
