package com.html_reader

import com.html_reader.backup.BackupStreamIo
import com.html_reader.backup.BackupTooLargeException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupStreamIoTest {

    private val chunkSize = 8 * 1024

    @Test
    fun readUtf8_readsWithinLimitAndClosesStream() = runBlocking {
        val input = TrackingInputStream("hello".toByteArray(Charsets.UTF_8))

        assertEquals("hello", BackupStreamIo.readUtf8(input, maxBytes = 5))

        assertTrue("读取成功后必须关闭输入流", input.closed)
    }

    @Test
    fun readUtf8_rejectsOverLimitAndClosesStream() = runBlocking {
        val input = TrackingInputStream("你好".toByteArray(Charsets.UTF_8)) // 6 bytes UTF-8

        val error = runCatching { BackupStreamIo.readUtf8(input, maxBytes = 5) }.exceptionOrNull()

        assertTrue("超限应抛出字节级异常", error is BackupTooLargeException)
        assertEquals(5L, (error as BackupTooLargeException).maxBytes)
        assertTrue("读取失败也必须关闭输入流", input.closed)
    }

    @Test
    fun readUtf8_limitIsBytesNotCharacters() = runBlocking {
        val bytes = "你好".toByteArray(Charsets.UTF_8) // 2 chars, 6 bytes

        assertEquals("你好", BackupStreamIo.readUtf8(ByteArrayInputStream(bytes), maxBytes = 6))
        assertTrue(
            runCatching { BackupStreamIo.readUtf8(ByteArrayInputStream(bytes), maxBytes = 5) }
                .exceptionOrNull() is BackupTooLargeException
        )
    }

    @Test
    fun readUtf8_handlesLargerThanBufferAndDoesNotReadPastLimit() = runBlocking {
        val payload = "a".repeat(chunkSize + 1).toByteArray(Charsets.UTF_8)
        val input = TrackingInputStream(payload)

        val error = runCatching { BackupStreamIo.readUtf8(input, maxBytes = chunkSize.toLong()) }.exceptionOrNull()

        assertTrue(error is BackupTooLargeException)
        assertTrue("超限后应立即失败并关闭流", input.closed)
    }

    @Test
    fun readUtf8_cancellationStopsNextChunkAndClosesStream() = runBlocking {
        val payload = ByteArray(chunkSize * 4)
        val jobRef = AtomicReference<Job?>()
        val input = CancellingInputStream(payload) { jobRef.get()?.cancel() }

        val job = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
            BackupStreamIo.readUtf8(input, maxBytes = payload.size.toLong() + 1)
        }
        jobRef.set(job)
        job.start()
        job.join()

        assertTrue("取消后任务应保持 CancellationException 状态", job.isCancelled)
        assertTrue("取消后输入流必须关闭", input.closed)
        assertEquals("取消后不应继续读下一块", 1, input.readCount)
    }

    @Test
    fun writeUtf8_writesTextAndClosesStream() = runBlocking {
        val output = TrackingOutputStream()

        BackupStreamIo.writeUtf8(output, "hello")

        assertEquals("hello", output.toString(Charsets.UTF_8.name()))
        assertTrue("写出后必须关闭输出流", output.closed)
        assertFalse(output.size() == 0)
    }

    @Test
    fun writeUtf8_failureStillClosesStream() = runBlocking {
        val output = FailingOutputStream()

        val error = runCatching { BackupStreamIo.writeUtf8(output, "hello") }.exceptionOrNull()

        assertTrue("写出失败应向上抛出", error is IOException)
        assertTrue("写出失败也必须关闭输出流", output.closed)
    }

    @Test
    fun writeUtf8_cancellationStopsNextChunkAndClosesStream() = runBlocking {
        val text = "a".repeat(chunkSize * 4)
        val jobRef = AtomicReference<Job?>()
        val output = CancellingOutputStream { jobRef.get()?.cancel() }

        val job = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
            BackupStreamIo.writeUtf8(output, text)
        }
        jobRef.set(job)
        job.start()
        job.join()

        assertTrue("取消后任务应保持 CancellationException 状态", job.isCancelled)
        assertTrue("取消后输出流必须关闭", output.closed)
        assertEquals("取消后不应继续写下一块", 1, output.writeCount)
    }

    private class TrackingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false
            private set

        override fun close() {
            closed = true
            super.close()
        }
    }

    private class CancellingInputStream(
        bytes: ByteArray,
        private val onFirstRead: () -> Unit
    ) : ByteArrayInputStream(bytes) {
        var readCount = 0
            private set
        var closed = false
            private set

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            readCount += 1
            val count = super.read(buffer, offset, length)
            if (readCount == 1) onFirstRead()
            return count
        }

        override fun close() {
            closed = true
            super.close()
        }
    }

    private class TrackingOutputStream : ByteArrayOutputStream() {
        var closed = false
            private set

        override fun close() {
            closed = true
            super.close()
        }
    }

    private class FailingOutputStream : ByteArrayOutputStream() {
        var closed = false
            private set

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            throw IOException("disk full")
        }

        override fun close() {
            closed = true
            super.close()
        }
    }

    private class CancellingOutputStream(
        private val onFirstWrite: () -> Unit
    ) : ByteArrayOutputStream() {
        var writeCount = 0
            private set
        var closed = false
            private set

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            writeCount += 1
            super.write(bytes, offset, length)
            if (writeCount == 1) onFirstWrite()
        }

        override fun close() {
            closed = true
            super.close()
        }
    }
}
