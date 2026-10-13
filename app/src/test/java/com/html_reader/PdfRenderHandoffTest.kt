package com.html_reader

import android.graphics.Bitmap
import core.reader.pdf.PdfRenderHandoff
import java.util.ArrayDeque
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class PdfRenderHandoffTest {

    @Test
    fun cancellationAfterProduction_releasesUnclaimedBitmapExactlyOnce() = runTest {
        val ioDispatcher = QueuedDispatcher()
        var produced: Bitmap? = null
        var releaseCount = 0
        val render = async {
            PdfRenderHandoff.withResource(
                dispatcher = ioDispatcher,
                release = { bitmap ->
                    releaseCount += 1
                    bitmap.recycle()
                }
            ) { owner ->
                Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).also {
                    produced = it
                    owner.own(it)
                }
            }
        }

        runCurrent()
        ioDispatcher.runAll()
        val bitmap = checkNotNull(produced)
        render.cancel()
        runCurrent()

        assertTrue(bitmap.isRecycled)
        assertEquals(1, releaseCount)
    }

    @Test
    fun successfulFallbackBitmap_transfersOwnershipToCaller() = runTest {
        val ioDispatcher = QueuedDispatcher()
        var releaseCount = 0
        val render = async {
            PdfRenderHandoff.withResource(
                dispatcher = ioDispatcher,
                release = { bitmap ->
                    releaseCount += 1
                    bitmap.recycle()
                }
            ) { owner ->
                owner.own(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
            }
        }

        runCurrent()
        ioDispatcher.runAll()
        runCurrent()
        val bitmap = render.await()

        assertFalse(bitmap.isRecycled)
        assertEquals(0, releaseCount)
        bitmap.recycle()
    }

    @Test
    fun producerFailureAfterBitmapCreation_releasesRegisteredBitmap() = runTest {
        supervisorScope {
            val ioDispatcher = QueuedDispatcher()
            var produced: Bitmap? = null
            var releaseCount = 0
            val render = async {
                PdfRenderHandoff.withResource(
                    dispatcher = ioDispatcher,
                    release = { bitmap ->
                        releaseCount += 1
                        bitmap.recycle()
                    }
                ) { owner ->
                    Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).also {
                        produced = it
                        owner.own(it)
                    }
                    error("page close failed")
                }
            }

            runCurrent()
            ioDispatcher.runAll()
            runCurrent()
            val failure = runCatching { render.await() }.exceptionOrNull()

            assertTrue(failure is IllegalStateException)
            assertTrue(checkNotNull(produced).isRecycled)
            assertEquals(1, releaseCount)
        }
    }

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            tasks.addLast(block)
        }

        fun runAll() {
            while (tasks.isNotEmpty()) {
                tasks.removeFirst().run()
            }
        }
    }
}
