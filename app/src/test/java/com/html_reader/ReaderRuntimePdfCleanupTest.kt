package com.html_reader

import android.graphics.Bitmap
import core.reader.pdf.PdfReaderController
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun closePdfControllerAfter_waitsForCancelledOpenBeforeClosing() = runBlocking {
        val scenario = createPendingOpenScenario(this)
        try {
            scenario.oldOpen.cancel()
            scenario.oldRender.cancel()
            val exit = ReaderRuntime.closePdfControllerAfter(
                previous = null,
                controller = scenario.controller,
                pendingJobs = listOf(scenario.oldOpen, scenario.oldRender)
            )
            val nextOpen = launch(Dispatchers.IO) {
                exit.join()
                scenario.controller.open(File("new.pdf"))
            }
            assertFalse(scenario.closedBeforePendingWork())
            scenario.finishOpen.complete(Unit)
            scenario.oldOpen.join()
            assertFalse(scenario.closedBeforePendingWork())
            scenario.finishRender.complete(Unit)
            scenario.oldRender.join()
            nextOpen.join()

            assertEquals(EXPECTED_EXIT_ORDER, scenario.order)
        } finally {
            scenario.releasePendingWork()
        }
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

    private suspend fun createPendingOpenScenario(scope: CoroutineScope): PendingOpenScenario {
        val order = mutableListOf<String>()
        val openStarted = CompletableDeferred<Unit>()
        val finishOpen = CompletableDeferred<Unit>()
        val renderStarted = CompletableDeferred<Unit>()
        val finishRender = CompletableDeferred<Unit>()
        val closed = CompletableDeferred<Unit>()
        val controller = RecordingPdfReaderController(
            onOpen = { file ->
                if (file.name == "old.pdf") {
                    order += "old-open-started"
                    withContext(NonCancellable) {
                        openStarted.complete(Unit)
                        finishOpen.await()
                    }
                    order += "old-open-finished"
                } else {
                    order += "new-open"
                }
            },
            onClose = {
                order += "close"
                closed.complete(Unit)
            }
        )
        val oldOpen = scope.launch(Dispatchers.IO) { controller.open(File("old.pdf")) }
        openStarted.await()
        val oldRender = scope.launch(Dispatchers.IO) {
            order += "old-render-started"
            withContext(NonCancellable) {
                renderStarted.complete(Unit)
                finishRender.await()
            }
            order += "old-render-finished"
        }
        renderStarted.await()
        return PendingOpenScenario(order, controller, oldOpen, oldRender, finishOpen, finishRender, closed)
    }

    private data class PendingOpenScenario(
        val order: MutableList<String>,
        val controller: RecordingPdfReaderController,
        val oldOpen: Job,
        val oldRender: Job,
        val finishOpen: CompletableDeferred<Unit>,
        val finishRender: CompletableDeferred<Unit>,
        private val closed: CompletableDeferred<Unit>
    ) {
        suspend fun closedBeforePendingWork(): Boolean = withTimeoutOrNull(100) {
            closed.await()
            true
        } ?: false

        fun releasePendingWork() {
            finishOpen.complete(Unit)
            finishRender.complete(Unit)
        }
    }

    private companion object {
        val EXPECTED_EXIT_ORDER = listOf(
            "old-open-started",
            "old-render-started",
            "old-open-finished",
            "old-render-finished",
            "close",
            "new-open"
        )
    }

    private class RecordingPdfReaderController(
        private val failOnClose: Boolean = false,
        private val onOpen: (suspend (File) -> Unit)? = null,
        private val onClose: (() -> Unit)? = null
    ) : PdfReaderController {
        var closeCount = 0
            private set

        override suspend fun open(file: File) {
            onOpen?.invoke(file)
        }

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
