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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * PDF 控制器：所有对 renderer/descriptor 的读写通过同一把 Mutex 串行化。
 * open/render/close 交错时不会出现「关闭旧 renderer 的同时还在 render」的情况。
 */
class AndroidPdfReaderController(
    private val dispatcherProvider: DispatcherProvider
) : PdfReaderController {
    private val mutex = Mutex()
    private val pageState = MutableStateFlow(0)
    private var pdfFileDescriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    override suspend fun open(file: File) {
        withContext(dispatcherProvider.io) {
            mutex.withLock {
                closeInternal()
                val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                try {
                    renderer = PdfRenderer(descriptor)
                    pdfFileDescriptor = descriptor
                } catch (t: Throwable) {
                    // renderer 构造失败时 descriptor 所有权尚未转移，必须显式关闭，避免 fd 泄漏
                    renderer = null
                    runCatching { descriptor.close() }
                    throw t
                }
                pageState.value = 0
            }
        }
    }

    override suspend fun goToPage(pageIndex: Int) {
        withContext(dispatcherProvider.io) {
            mutex.withLock {
                val count = renderer?.pageCount ?: 0
                if (count <= 0) {
                    pageState.value = 0
                } else {
                    pageState.value = pageIndex.coerceIn(0, count - 1)
                }
            }
        }
    }

    override suspend fun getPageCount(): Int = withContext(dispatcherProvider.io) {
        mutex.withLock { renderer?.pageCount ?: 0 }
    }

    override suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap {
        var unclaimed: Bitmap? = null
        try {
            val rendered = withContext(dispatcherProvider.io) {
                mutex.withLock {
                    // 在销毁边界前先登记未交付资源；若 withContext 因取消丢弃返回值，finally 负责回收。
                    renderLocked(pageIndex, targetWidthPx).also { unclaimed = it }
                }
            }
            unclaimed = null
            return rendered
        } finally {
            val pending = unclaimed
            if (pending != null && !pending.isRecycled) pending.recycle()
        }
    }

    private fun renderLocked(pageIndex: Int, targetWidthPx: Int): Bitmap {
        val localRenderer = renderer ?: throw IllegalStateException("PDF not opened")
        val count = localRenderer.pageCount
        if (count <= 0) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        val safePage = pageIndex.coerceIn(0, count - 1)
        localRenderer.openPage(safePage).use { page ->
            val width = targetWidthPx.coerceAtLeast(1)
            val scale = width.toFloat() / page.width.toFloat().coerceAtLeast(1f)
            val height = (page.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            try {
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            } catch (t: Throwable) {
                // render 失败时调用方不会收到该 bitmap，必须在此回收
                bitmap.recycle()
                throw t
            }
            return bitmap
        }
    }

    override suspend fun close() {
        withContext(dispatcherProvider.io) {
            mutex.withLock { closeInternal() }
        }
    }

    private fun closeInternal() {
        val rendererToClose = renderer
        val descriptorToClose = pdfFileDescriptor
        renderer = null
        pdfFileDescriptor = null
        try {
            rendererToClose?.close()
        } finally {
            descriptorToClose?.close()
        }
    }

    override fun observePageChanges(): Flow<Int> = pageState
}
