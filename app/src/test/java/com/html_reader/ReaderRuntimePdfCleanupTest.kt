package com.html_reader

import android.graphics.Bitmap
import core.reader.pdf.PdfReaderController
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReaderRuntimePdfCleanupTest {

    @Test
    fun closePdfController_closesOnlyProvidedInstance() = runBlocking {
        val first = RecordingPdfReaderController()
        val second = RecordingPdfReaderController()

        ReaderRuntime.closePdfController(first).join()
        ReaderRuntime.closePdfController(second).join()

        assertEquals(1, first.closeCount)
        assertEquals(1, second.closeCount)
    }

    @Test
    fun closePdfControllerAfter_waitsForPreviousClose() = runBlocking {
        val order = mutableListOf<String>()
        val first = RecordingPdfReaderController(onClose = { order += "first" })
        val second = RecordingPdfReaderController(onClose = { order += "second" })

        val firstJob = ReaderRuntime.closePdfController(first)
        val secondJob = ReaderRuntime.closePdfControllerAfter(firstJob, second)
        secondJob.join()

        assertEquals(listOf("first", "second"), order)
    }

    @Test
    fun closePdfController_survivesCloseFailure() = runBlocking {
        val failing = RecordingPdfReaderController(failOnClose = true)
        val normal = RecordingPdfReaderController()

        ReaderRuntime.closePdfController(failing).join()
        ReaderRuntime.closePdfController(normal).join()

        assertEquals(1, failing.closeCount)
        assertEquals(1, normal.closeCount)
    }

    private class RecordingPdfReaderController(
        private val failOnClose: Boolean = false,
        private val onClose: (() -> Unit)? = null
    ) : PdfReaderController {
        var closeCount = 0
            private set

        override suspend fun open(file: File) = Unit

        override suspend fun goToPage(pageIndex: Int) = Unit

        override suspend fun getPageCount(): Int = 0

        override suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap =
            throw UnsupportedOperationException("not used")

        override suspend fun close() {
            closeCount += 1
            onClose?.invoke()
            if (failOnClose) throw IllegalStateException("close failed")
        }

        override fun observePageChanges(): Flow<Int> = emptyFlow()
    }
}
