package com.html_reader.files

import androidx.test.core.app.ApplicationProvider
import core.database.entity.NetworkConfigEntity
import core.database.entity.enums.NetworkProtocol
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.util.ArrayDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class FilesTransferResourceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun smbContext_usesConfiguredConnectSocketAndResponseTimeouts() {
        val context = FilesSmbGateway.smbContext(smbConfig())
        val config = context.config

        assertEquals(FilesSmbGateway.SMB_CONNECT_TIMEOUT_MS, config.connTimeout)
        assertEquals(FilesSmbGateway.SMB_SOCKET_TIMEOUT_MS, config.soTimeout)
        assertEquals(FilesSmbGateway.SMB_RESPONSE_TIMEOUT_MS, config.responseTimeout)
    }

    @Test
    fun ftpDownload_reopensAndReplacesSameLengthContent_closingEachResource() = runBlocking {
        val factory = FakeResourceFactory().apply {
            inputPayloads.add("AAAA".toByteArray())
            inputPayloads.add("BBBB".toByteArray())
        }
        val config = ftpConfig()

        val first = FilesTransferGateway.downloadFtpToLocal(
            tempFolder.root, config, "/docs/a.mhtml", "a.mhtml", "UTF-8", factory
        )
        assertEquals("AAAA", first.readText())

        val second = FilesTransferGateway.downloadFtpToLocal(
            tempFolder.root, config, "/docs/a.mhtml", "a.mhtml", "UTF-8", factory
        )

        assertEquals(first, second)
        assertEquals("BBBB", second.readText())
        assertEquals(2, factory.resourceCloseCount)
        assertEquals(2, factory.inputCloseCount)
        assertEquals(FilesTransferGateway.FTP_CONNECT_TIMEOUT_MS, factory.lastFtpConnectTimeout)
        assertEquals(FilesTransferGateway.FTP_READ_TIMEOUT_MS, factory.lastFtpReadTimeout)
    }

    @Test
    fun smbDownload_closesRemoteFileAndStreams() = runBlocking {
        val factory = FakeResourceFactory().apply { inputPayloads.add("SMB".toByteArray()) }

        val result = FilesTransferGateway.downloadSmbToLocal(
            tempFolder.root, smbConfig(), "/share/a.pdf", "a.pdf", factory
        )

        assertEquals("SMB", result.readText())
        assertEquals(1, factory.resourceCloseCount)
        assertEquals(1, factory.inputCloseCount)
        assertTrue(factory.lastSmbUrl!!.endsWith("/share/a.pdf"))
    }

    @Test
    fun smbUpload_failureAtOutputCreation_isReportedAsPartialAndRecorded() = runBlocking {
        val attempt = RecordingAttempt()
        val factory = FakeResourceFactory().apply {
            outputProvider = {
                remoteOutputMayHaveBeenTruncated = true
                throw IOException("server disconnected while opening output")
            }
        }

        val error = runCatching {
            FilesTransferGateway.uploadSmbFromStream(
                targetUrl = "smb://server/share/file.bin",
                inputStream = { ByteArrayInputStream(byteArrayOf()) },
                config = smbConfig(),
                targetPath = "/share/file.bin",
                attempt = attempt,
                resourceFactory = factory
            )
        }.exceptionOrNull()

        assertTrue(factory.remoteOutputMayHaveBeenTruncated)
        assertTrue(attempt.remoteWriteMayHaveStarted)
        assertTrue(error is FilesTransferException.PartialUpload)
        assertEquals(1, factory.resourceCloseCount)
    }

    @Test
    fun ftpUpload_missingInput_doesNotMarkRemoteWriteRisk() = runBlocking {
        val attempt = RecordingAttempt()
        val factory = FakeResourceFactory()

        val error = runCatching {
            FilesTransferGateway.uploadFtpFromStream(
                targetUrl = URL("ftp://example.com/file.bin"),
                inputStream = { null },
                config = ftpConfig(),
                targetPath = "/file.bin",
                attempt = attempt,
                resourceFactory = factory
            )
        }.exceptionOrNull()

        assertTrue(error is FilesTransferException.InputUnavailable)
        assertFalse(attempt.remoteWriteMayHaveStarted)
        assertFalse(factory.remoteOutputMayHaveBeenTruncated)
        assertEquals(1, factory.openCount)
    }

    @Test
    fun ftpUpload_cancelledBeforeOutputCreation_closesResourceWithoutWarning() = runBlocking {
        val attempt = RecordingAttempt()
        val jobRef = java.util.concurrent.atomic.AtomicReference<Job?>()
        val factory = FakeResourceFactory().apply { onOpen = { jobRef.get()?.cancel() } }
        val job = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
            FilesTransferGateway.uploadFtpFromStream(
                targetUrl = URL("ftp://example.com/file.bin"),
                inputStream = { ByteArrayInputStream(byteArrayOf(1)) },
                config = ftpConfig(),
                targetPath = "/file.bin",
                attempt = attempt,
                resourceFactory = factory
            )
        }
        jobRef.set(job)
        job.start()
        job.join()

        assertTrue(job.isCancelled)
        assertFalse(attempt.remoteWriteMayHaveStarted)
        assertFalse(factory.remoteOutputMayHaveBeenTruncated)
        assertEquals(1, factory.resourceCloseCount)
    }

    @Test
    fun ftpUpload_successClearsAttemptAfterOutputCloses() = runBlocking {
        val attempt = RecordingAttempt()
        val output = ByteArrayOutputStream()
        val factory = FakeResourceFactory().apply { outputProvider = { output } }

        FilesTransferGateway.uploadFtpFromStream(
            targetUrl = URL("ftp://example.com/file.bin"),
            inputStream = { ByteArrayInputStream("payload".toByteArray()) },
            config = ftpConfig(),
            targetPath = "/file.bin",
            attempt = attempt,
            resourceFactory = factory
        )

        assertEquals("payload", output.toString("UTF-8"))
        assertEquals(1, factory.outputCloseCount)
        assertFalse(attempt.remoteWriteMayHaveStarted)
        assertEquals(1, attempt.completedCount)
        assertEquals("FTP://ftp.example.test:21/file.bin", attempt.targetDescription)
        assertFalse(attempt.targetDescription.orEmpty().contains("secret"))
        assertEquals(true, factory.lastFtpDoOutput)
        assertEquals(FilesTransferGateway.FTP_CONNECT_TIMEOUT_MS, factory.lastFtpConnectTimeout)
        assertEquals(FilesTransferGateway.FTP_READ_TIMEOUT_MS, factory.lastFtpReadTimeout)
    }

    @Test
    fun smbUpload_cancellationAfterRemoteWriteStarts_isRethrownUnwrapped() = runBlocking {
        val attempt = RecordingAttempt()
        val cancellation = CancellationException("cancelled during remote write")
        val factory = FakeResourceFactory().apply {
            outputProvider = {
                object : OutputStream() {
                    override fun write(value: Int) = throw cancellation
                }
            }
        }

        val error = runCatching {
            FilesTransferGateway.uploadSmbFromStream(
                targetUrl = "smb://server/share/file.bin",
                inputStream = { ByteArrayInputStream(byteArrayOf(1)) },
                config = smbConfig(),
                targetPath = "/share/file.bin",
                attempt = attempt,
                resourceFactory = factory
            )
        }.exceptionOrNull()

        assertTrue(error is CancellationException)
        assertEquals(cancellation.message, error?.message)
        assertFalse(error is FilesTransferException.PartialUpload)
        assertTrue(attempt.remoteWriteMayHaveStarted)
    }

    @Test
    fun pendingUploadStore_survivesStoreRecreationUntilAcknowledged() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("pending_uploads", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val firstStore = FilesPendingUploadStore(context)
        val attempt = firstStore.newAttempt()
        attempt.setRemoteTarget("SMB://server/share/a.bin")

        attempt.markRemoteWriteMayHaveStarted()

        val reopenedStore = FilesPendingUploadStore(RuntimeEnvironment.getApplication())
        assertTrue(reopenedStore.hasPendingUploads())
        assertEquals(listOf("SMB://server/share/a.bin"), reopenedStore.pendingDescriptions())
        assertTrue(FilesPendingUploadStore(context).acknowledgePendingUploads())
        assertFalse(firstStore.hasPendingUploads())
    }

    @Test
    fun partialUploadError_usesDedicatedLocalizedWarning() {
        val texts = NetworkErrorTexts(
            ftpAuthFailed = "ftp auth",
            smbAuthFailed = "smb auth",
            ftpConnectionFailed = "ftp connection",
            smbConnectionFailed = "smb connection",
            defaultMessage = "default",
            partialUpload = "check remote target"
        )

        val message = FilesErrorFormatter.format(
            FilesTransferException.PartialUpload(IOException("write failed")),
            NetworkProtocol.SMB,
            texts
        )

        assertEquals("check remote target", message)
    }

    private fun ftpConfig() = NetworkConfigEntity(
        name = "test",
        protocol = NetworkProtocol.FTP,
        host = "ftp.example.test",
        port = 21,
        username = "user",
        password = "secret",
        defaultPath = "/",
        encoding = "UTF-8"
    )

    private fun smbConfig() = NetworkConfigEntity(
        name = "test",
        protocol = NetworkProtocol.SMB,
        host = "smb.example.test",
        port = 445,
        username = "user",
        password = "secret",
        defaultPath = "/",
        encoding = "Auto"
    )

    private class RecordingAttempt : FilesUploadAttempt {
        override var remoteWriteMayHaveStarted: Boolean = false
            private set
        var targetDescription: String? = null
            private set
        var completedCount = 0
            private set

        override fun setRemoteTarget(description: String) {
            targetDescription = description
        }

        override fun markRemoteWriteMayHaveStarted() {
            remoteWriteMayHaveStarted = true
        }

        override fun markCompleted() {
            completedCount++
            remoteWriteMayHaveStarted = false
        }
    }

    private class FakeResourceFactory : FilesTransferResourceFactory {
        val inputPayloads = ArrayDeque<ByteArray>()
        var outputProvider: () -> OutputStream = { ByteArrayOutputStream() }
        var resourceCloseCount = 0
        var inputCloseCount = 0
        var outputCloseCount = 0
        var openCount = 0
        var remoteOutputMayHaveBeenTruncated = false
        var lastFtpConnectTimeout: Int? = null
        var lastFtpReadTimeout: Int? = null
        var lastFtpDoOutput: Boolean? = null
        var lastSmbUrl: String? = null
        var onOpen: (() -> Unit)? = null

        override fun openFtp(
            url: URL,
            doOutput: Boolean,
            connectTimeoutMs: Int,
            readTimeoutMs: Int
        ): FilesTransferResource {
            openCount++
            lastFtpDoOutput = doOutput
            lastFtpConnectTimeout = connectTimeoutMs
            lastFtpReadTimeout = readTimeoutMs
            onOpen?.invoke()
            val payload = inputPayloads.pollFirst()
            return resource(
                input = payload?.let(::ByteArrayInputStream),
                output = if (doOutput) outputProvider else null
            )
        }

        override fun openSmb(url: String, context: jcifs.CIFSContext): FilesTransferResource {
            openCount++
            lastSmbUrl = url
            val payload = inputPayloads.pollFirst()
            return resource(
                input = payload?.let(::ByteArrayInputStream),
                output = outputProvider
            )
        }

        private fun resource(input: InputStream?, output: (() -> OutputStream)?) = object : FilesTransferResource {
            override fun inputStream(): InputStream = object : FilterInputStream(checkNotNull(input)) {
                override fun close() {
                    super.close()
                    inputCloseCount++
                }
            }

            override fun outputStream(): OutputStream {
                remoteOutputMayHaveBeenTruncated = true
                val delegate = checkNotNull(output).invoke()
                return object : OutputStream() {
                    override fun write(value: Int) = delegate.write(value)

                    override fun write(bytes: ByteArray, offset: Int, length: Int) =
                        delegate.write(bytes, offset, length)

                    override fun close() {
                        try {
                            delegate.close()
                        } finally {
                            outputCloseCount++
                        }
                    }
                }
            }

            override fun close() {
                resourceCloseCount++
            }
        }
    }
}
