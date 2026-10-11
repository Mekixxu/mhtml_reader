package com.html_reader

import core.common.DefaultDispatcherProvider
import core.reader.pdf.impl.AndroidPdfReaderController
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidPdfReaderControllerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val dispatcher = DefaultDispatcherProvider()

    @Test
    fun close_isIdempotentWithoutOpen() = runBlocking {
        val controller = AndroidPdfReaderController(dispatcher)
        controller.close()
        controller.close()
    }

    @Test
    fun renderPage_beforeOpen_throws() = runBlocking {
        val controller = AndroidPdfReaderController(dispatcher)
        val error = runCatching { controller.renderPage(0, 120) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        controller.close()
    }

    @Test
    fun open_corruptedPdf_throwsAndClosesDescriptor() = runBlocking {
        val controller = AndroidPdfReaderController(dispatcher)
        val file = tempFolder.newFile("corrupted.pdf").apply { writeText("not a real pdf") }

        assertTrue(runCatching { controller.open(file) }.isFailure)

        // open 失败后控制器应保持可关闭且重复关闭幂等
        controller.close()
        controller.close()
    }
}
