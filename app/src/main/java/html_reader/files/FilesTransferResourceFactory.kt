package com.html_reader.files

import jcifs.CIFSContext
import jcifs.smb.SmbFile
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.URL

internal interface FilesTransferResource : Closeable {
    fun inputStream(): InputStream

    fun outputStream(): OutputStream
}

internal interface FilesTransferResourceFactory {
    fun openFtp(
        url: URL,
        doOutput: Boolean,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): FilesTransferResource

    fun openSmb(url: String, context: CIFSContext): FilesTransferResource
}

internal object DefaultFilesTransferResourceFactory : FilesTransferResourceFactory {
    override fun openFtp(
        url: URL,
        doOutput: Boolean,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): FilesTransferResource {
        val connection = url.openConnection().apply {
            this.doOutput = doOutput
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
        }
        return object : FilesTransferResource {
            override fun inputStream(): InputStream = connection.getInputStream()

            override fun outputStream(): OutputStream = connection.getOutputStream()

            override fun close() = Unit
        }
    }

    override fun openSmb(url: String, context: CIFSContext): FilesTransferResource {
        val file = SmbFile(url, context)
        return object : FilesTransferResource {
            override fun inputStream(): InputStream = file.inputStream

            override fun outputStream(): OutputStream = file.outputStream

            override fun close() {
                file.close()
            }
        }
    }
}
