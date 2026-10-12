package com.html_reader.files

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FilesTransferGatewayTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun copyCancellable_copiesAllBytesNormally() = runBlocking {
        val payload = ByteArray(256 * 1024) { it.toByte() }
        val output = ByteArrayOutputStream()

        FilesTransferGateway.copyCancellable(ByteArrayInputStream(payload), output)

        assertTrue(payload.contentEquals(output.toByteArray()))
    }

    @Test
    fun copyCancellable_stopsAfterCancellation() = runBlocking {
        val chunkSize = 64 * 1024
        val payload = ByteArray(chunkSize * 4) { it.toByte() }
        val jobRef = AtomicReference<Job?>()
        val output = object : ByteArrayOutputStream() {
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                super.write(bytes, offset, length)
                jobRef.get()?.cancel()
            }
        }
        val job = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
            FilesTransferGateway.copyCancellable(ByteArrayInputStream(payload), output)
        }
        jobRef.set(job)
        job.start()
        job.join()

        assertTrue("取消后 Must 不应继续复制完整文件", job.isCancelled)
        assertTrue(output.size() < payload.size)
        assertTrue("首个块已写出，后续块应停止", output.size() <= chunkSize)
    }

    @Test
    fun writeAtomically_failureDeletesTempAndKeepsTarget() = runBlocking {
        val dir = tempFolder.newFolder("atomic-fail")
        val target = File(dir, "doc.mhtml").apply { writeText("old") }

        val error = runCatching {
            FilesTransferGateway.writeAtomically(target) { throw IOException("boom") }
        }.exceptionOrNull()

        assertTrue(error is IOException)
        assertEquals("old", target.readText())
        assertFalse(dir.listFiles()?.any { it.name.contains(".part-") } ?: true)
    }

    @Test
    fun mapTransferError_preservesCancellationAndMapsIo() {
        val cancellation = CancellationException("cancelled")
        assertSame(cancellation, FilesTransferGateway.mapTransferError(cancellation))

        val mapped = FilesTransferGateway.mapTransferError(IOException("connect refused"))
        assertTrue(mapped is FilesTransferException.ConnectionFailed)
    }
}
