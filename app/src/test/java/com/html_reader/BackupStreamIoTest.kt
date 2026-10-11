package com.html_reader

import com.html_reader.backup.BackupStreamIo
import com.html_reader.backup.BackupTooLargeException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupStreamIoTest {

    @Test
    fun readUtf8_readsWithinLimitAndClosesStream() {
        val input = TrackingInputStream("hello".toByteArray(Charsets.UTF_8))

        assertEquals("hello", BackupStreamIo.readUtf8(input, maxBytes = 5))

        assertTrue("读取成功后必须关闭输入流", input.closed)
    }

    @Test
    fun readUtf8_rejectsOverLimitAndClosesStream() {
        val input = TrackingInputStream("你好".toByteArray(Charsets.UTF_8)) // 6 bytes UTF-8

        val error = runCatching { BackupStreamIo.readUtf8(input, maxBytes = 5) }.exceptionOrNull()

        assertTrue("超限应抛出字节级异常", error is BackupTooLargeException)
        assertEquals(5L, (error as BackupTooLargeException).maxBytes)
        assertTrue("读取失败也必须关闭输入流", input.closed)
    }

    @Test
    fun readUtf8_limitIsBytesNotCharacters() {
        val bytes = "你好".toByteArray(Charsets.UTF_8) // 2 chars, 6 bytes

        assertEquals("你好", BackupStreamIo.readUtf8(ByteArrayInputStream(bytes), maxBytes = 6))
        assertTrue(
            runCatching { BackupStreamIo.readUtf8(ByteArrayInputStream(bytes), maxBytes = 5) }
                .exceptionOrNull() is BackupTooLargeException
        )
    }

    @Test
    fun readUtf8_handlesLargerThanBufferAndDoesNotReadPastLimit() {
        val payload = "a".repeat(9000).toByteArray(Charsets.UTF_8)
        val input = TrackingInputStream(payload)

        val error = runCatching { BackupStreamIo.readUtf8(input, maxBytes = 8192) }.exceptionOrNull()

        assertTrue(error is BackupTooLargeException)
        assertTrue("超限后应立即失败并关闭流", input.closed)
    }

    @Test
    fun writeUtf8_writesTextAndClosesStream() {
        val output = TrackingOutputStream()

        BackupStreamIo.writeUtf8(output, "hello")

        assertEquals("hello", output.toString(Charsets.UTF_8.name()))
        assertTrue("写出后必须关闭输出流", output.closed)
        assertFalse(output.size() == 0)
    }

    @Test
    fun writeUtf8_failureStillClosesStream() {
        val output = FailingOutputStream()

        val error = runCatching { BackupStreamIo.writeUtf8(output, "hello") }.exceptionOrNull()

        assertTrue("写出失败应向上抛出", error is java.io.IOException)
        assertTrue("写出失败也必须关闭输出流", output.closed)
    }

    private class TrackingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false
            private set

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
            throw java.io.IOException("disk full")
        }

        override fun close() {
            closed = true
            super.close()
        }
    }
}
