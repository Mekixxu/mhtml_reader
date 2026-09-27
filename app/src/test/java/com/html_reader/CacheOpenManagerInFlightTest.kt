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
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CacheOpenManagerInFlightTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test(timeout = 120_000)
    fun inFlightCopy_isProtectedFromEviction() = runBlocking {
        val cacheRoot = File(tempFolder.root, "app_cache")
        val midCopy = CountDownLatch(1)
        val release = CountDownLatch(1)

        // A（a.mhtml）写到 100 字节后阻塞；B（b.mhtml）立即可完成，避免测试自身阻塞
        val fileSystem = object : StubFileSystem() {
            override suspend fun openInputStream(path: VfsPath): Result<InputStream> {
                val blocking = (path as? VfsPath.LocalFile)?.filePath?.endsWith("a.mhtml") == true
                return Result.success(object : InputStream() {
                    private var emitted = 0
                    override fun read(): Int = read(ByteArray(1), 0, 1)

                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        val limit = if (blocking) 100 else 150
                        if (emitted < limit) {
                            val n = minOf(len, limit - emitted)
                            java.util.Arrays.fill(b, off, off + n, 'x'.code.toByte())
                            emitted += n
                            return n
                        }
                        if (blocking) {
                            // 先返回 100 字节触发落盘，下一次读取再阻塞，确保 A 已占用缓存
                            midCopy.countDown()
                            release.await(30, TimeUnit.SECONDS)
                        }
                        return -1
                    }
                })
            }
        }

        val manager = CacheOpenManager(
            context = context,
            cacheRoot = cacheRoot,
            fileSystem = fileSystem,
            dispatcherProvider = DefaultDispatcherProvider(),
            cacheEvictor = CacheEvictor(cacheRoot = cacheRoot, maxBytes = 200)
        )

        val resultsA = mutableListOf<Result<CopyProgress>>()
        val jobA = launch(Dispatchers.IO) {
            manager.openToCache(
                src = VfsPath.LocalFile("/tmp/a.mhtml"),
                totalBytes = 100,
                contentType = ContentType.MHTML
            ).collect { synchronized(resultsA) { resultsA.add(it) } }
        }

        // 等 A 写到一半（已占 100 字节缓存）
        val aIsCopying = withContext(Dispatchers.IO) { midCopy.await(10, TimeUnit.SECONDS) }
        assertTrue("A 应处于拷贝中", aIsCopying)

        // B 需要 150 字节：若删除在途的 A 才能满足；受保护时应直接失败
        val resultsB = manager.openToCache(
            src = VfsPath.LocalFile("/tmp/b.mhtml"),
            totalBytes = 150,
            contentType = ContentType.MHTML
        ).toList()

        release.countDown()
        jobA.join()

        assertTrue("B 应因无法腾出空间失败", resultsB.last().isFailure)
        val finalA = synchronized(resultsA) { resultsA.lastOrNull() }
        assertEquals(true, finalA?.isSuccess)
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
