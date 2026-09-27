package com.html_reader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import core.cache.CacheEvictor
import core.cache.CacheOpenManager
import core.cache.model.ContentType
import core.cache.model.CopyProgress
import core.common.AppError
import core.common.DefaultDispatcherProvider
import core.vfs.IFileSystem
import core.vfs.model.VfsEntry
import core.vfs.model.VfsPath
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CacheOpenManagerFailureTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun manager(fileSystem: IFileSystem, maxBytes: Long = 10L * 1024 * 1024): CacheOpenManager {
        val cacheRoot = File(tempFolder.root, "app_cache")
        return CacheOpenManager(
            context = context,
            cacheRoot = cacheRoot,
            fileSystem = fileSystem,
            dispatcherProvider = DefaultDispatcherProvider(),
            cacheEvictor = CacheEvictor(cacheRoot = cacheRoot, maxBytes = maxBytes)
        )
    }

    @Test
    fun openInputStreamFailure_emitsSingleFailureWithoutSuccess() = runBlocking {
        val failing = object : StubFileSystem() {
            override suspend fun openInputStream(path: VfsPath): Result<InputStream> =
                Result.failure(AppError.NotFound)
        }

        val results = manager(failing).openToCache(
            src = VfsPath.LocalFile("/tmp/missing.mhtml"),
            totalBytes = 100,
            contentType = ContentType.MHTML
        ).toList()

        assertEquals(1, results.size)
        assertTrue(results.single().isFailure)
    }

    @Test
    fun copyFailure_emitsSingleFailureWithoutFakeSuccess() = runBlocking {
        val failing = object : StubFileSystem() {
            override suspend fun openInputStream(path: VfsPath): Result<InputStream> =
                Result.success(object : InputStream() {
                    override fun read(): Int = throw IOException("disk read error")
                })
        }

        val results = manager(failing).openToCache(
            src = VfsPath.LocalFile("/tmp/broken.mhtml"),
            totalBytes = 100,
            contentType = ContentType.MHTML
        ).toList()

        assertEquals(1, results.size)
        assertTrue(results.single().isFailure)
    }

    @Test
    fun oversizedFile_emitsFailureWithoutDeletingExistingCache() = runBlocking {
        val cacheRoot = File(tempFolder.root, "app_cache")
        val existing = File(cacheRoot, "mhtml/keep/content.mhtml").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(100))
        }
        val fileSystem = object : StubFileSystem() {}
        val openManager = CacheOpenManager(
            context = context,
            cacheRoot = cacheRoot,
            fileSystem = fileSystem,
            dispatcherProvider = DefaultDispatcherProvider(),
            cacheEvictor = CacheEvictor(cacheRoot = cacheRoot, maxBytes = 200)
        )

        val results = openManager.openToCache(
            src = VfsPath.LocalFile("/tmp/big.mhtml"),
            totalBytes = 500,
            contentType = ContentType.MHTML
        ).toList()

        assertEquals(1, results.size)
        assertTrue(results.single().isFailure)
        assertTrue(existing.exists())
    }

    private open class StubFileSystem : IFileSystem {
        override suspend fun list(dir: VfsPath, offset: Int, limit: Int): Result<List<VfsEntry>> =
            Result.success(emptyList())
        override suspend fun openInputStream(path: VfsPath): Result<InputStream> =
            Result.failure(AppError.NotFound)
        override suspend fun openOutputStream(path: VfsPath, append: Boolean): Result<OutputStream> =
            Result.failure(AppError.UnsupportedOperation)
        override suspend fun createFile(parentDir: VfsPath, name: String, mimeType: String?): Result<VfsPath> =
            Result.failure(AppError.UnsupportedOperation)
        override suspend fun exists(path: VfsPath): Result<Boolean> = Result.success(false)
        override suspend fun createFolder(path: VfsPath): Result<Unit> = Result.failure(AppError.UnsupportedOperation)
        override suspend fun delete(path: VfsPath): Result<Unit> = Result.failure(AppError.UnsupportedOperation)
        override suspend fun rename(from: VfsPath, toName: String): Result<VfsPath> =
            Result.failure(AppError.UnsupportedOperation)
        override suspend fun move(from: VfsPath, toDir: VfsPath): Result<VfsPath> =
            Result.failure(AppError.UnsupportedOperation)
        override suspend fun copy(from: VfsPath, toDir: VfsPath): Result<VfsPath> =
            Result.failure(AppError.UnsupportedOperation)
        override suspend fun lastModified(path: VfsPath): Result<Long> = Result.failure(AppError.UnsupportedOperation)
    }
}
