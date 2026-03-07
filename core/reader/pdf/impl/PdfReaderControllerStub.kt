package core.reader.pdf.impl

import android.graphics.Bitmap
import core.reader.pdf.PdfReaderController
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow

class PdfReaderControllerStub : PdfReaderController {
    private val pageState = MutableStateFlow(0)
    override suspend fun open(file: File) {
        pageState.value = 0
    }
    override suspend fun goToPage(pageIndex: Int) {
        pageState.value = pageIndex
    }
    override suspend fun getPageCount(): Int = 1
    override suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap {
        pageState.value = pageIndex.coerceAtLeast(0)
        return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }
    override suspend fun close() = Unit
    override fun observePageChanges(): Flow<Int> = pageState
}
