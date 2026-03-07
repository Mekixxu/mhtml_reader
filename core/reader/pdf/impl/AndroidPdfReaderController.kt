package core.reader.pdf.impl

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import core.common.DispatcherProvider
import core.reader.pdf.PdfReaderController
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

class AndroidPdfReaderController(
    private val dispatcherProvider: DispatcherProvider
) : PdfReaderController {
    private val pageState = MutableStateFlow(0)
    private var pdfFileDescriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    override suspend fun open(file: File) {
        withContext(dispatcherProvider.io) {
            closeInternal()
            pdfFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pdfFileDescriptor!!)
            pageState.value = 0
        }
    }

    override suspend fun goToPage(pageIndex: Int) {
        withContext(dispatcherProvider.io) {
            val count = renderer?.pageCount ?: 0
            if (count <= 0) {
                pageState.value = 0
                return@withContext
            }
            pageState.value = pageIndex.coerceIn(0, count - 1)
        }
    }

    override suspend fun getPageCount(): Int = withContext(dispatcherProvider.io) {
        renderer?.pageCount ?: 0
    }

    override suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap =
        withContext(dispatcherProvider.io) {
            val localRenderer = renderer ?: throw IllegalStateException("PDF not opened")
            val count = localRenderer.pageCount
            if (count <= 0) {
                return@withContext Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            }
            val safePage = pageIndex.coerceIn(0, count - 1)
            localRenderer.openPage(safePage).use { page ->
                val width = targetWidthPx.coerceAtLeast(1)
                val scale = width.toFloat() / page.width.toFloat().coerceAtLeast(1f)
                val height = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }

    override suspend fun close() {
        withContext(dispatcherProvider.io) {
            closeInternal()
        }
    }

    private fun closeInternal() {
        renderer?.close()
        renderer = null
        pdfFileDescriptor?.close()
        pdfFileDescriptor = null
    }

    override fun observePageChanges(): Flow<Int> = pageState
}
