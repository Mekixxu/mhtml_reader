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
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Q4 缓存完整性回归：只有校验通过的完整拷贝才能提交成品并上报成功。
 * 正式回归（非探针）：断言正确期望，覆盖截短、精确、增长、已知空文件、未知长度与取消清理。
 */
@RunWith(RobolectricTestRunner::class)
class CacheOpenManagerIntegrityTest {

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

    private fun cacheRoot(): File = File(tempFolder.root, "app_cache")

    private fun payloadStream(size: Int): InputStream =
        ByteArrayInputStream(ByteArray(size) { 'x'.code.toByte() })

    private fun cacheArtifacts(): List<File> =
        cacheRoot().takeIf { it.exists() }?.walkTopDown()?.filter { it.isFile }?.toList() ?: emptyList()

    @Test
    fun truncatedSource_declared100actual60_emitsErrorWithoutHalfProduct() = runBlocking {
        val fileSystem = StubFileSystem { Result.success(payloadStream(60)) }

        val results = manager(fileSystem).openToCache(
            src = VfsPath.LocalFile("/tmp/truncated.mhtml"),
            totalBytes = 100,
            contentType = ContentType.MHTML
        ).toList()

        assertTrue("最后一帧必须是失败", results.last().isFailure)
        assertTrue("失败必须可识别为完整性错误", results.last().exceptionOrNull() is AppError.IoError)
        assertTrue("不得残留任何成品或半成品", cacheArtifacts().isEmpty())
    }

    @Test
    fun exactLengthCopy_succeedsAndReportsActualBytes() = runBlocking {
        val fileSystem = StubFileSystem { Result.success(payloadStream(100)) }
        val openManager = manager(fileSystem)

        val results = openManager.openToCache(
            src = VfsPath.LocalFile("/tmp/exact.mhtml"),
            totalBytes = 100,
            contentType = ContentType.MHTML
        ).toList()

        val success = results.last()
        assertTrue("精确长度必须成功", success.isSuccess)
        val progress = success.getOrThrow()
        assertEquals(100L, progress.copiedBytes)
        assertEquals(100L, progress.totalBytes)

        val cacheKey = openManager.generateCacheKey(VfsPath.LocalFile("/tmp/exact.mhtml"), ContentType.MHTML, 100)
        val committed = openManager.resolveCacheFile(ContentType.MHTML, cacheKey, "mhtml")
        assertTrue("成品必须已提交", committed.exists())
        assertEquals(100L, committed.length())
        assertTrue("不得残留 .part", cacheArtifacts().none { it.name.endsWith(".part") })
    }

    @Test
    fun sourceGrewBeyondExpected_doesNotFakeSuccess() = runBlocking {
        val fileSystem = StubFileSystem { Result.success(payloadStream(140)) }

        val results = manager(fileSystem).openToCache(
            src = VfsPath.LocalFile("/tmp/grown.mhtml"),
            totalBytes = 100,
            contentType = ContentType.MHTML
        ).toList()

        assertTrue("源增长不得假成功", results.last().isFailure)
        assertTrue("不得残留任何成品或半成品", cacheArtifacts().isEmpty())
    }

    @Test
    fun knownEmptyFile_commitsZeroByteProduct() = runBlocking {
        val fileSystem = StubFileSystem { Result.success(payloadStream(0)) }
        val openManager = manager(fileSystem)

        val results = openManager.openToCache(
            src = VfsPath.LocalFile("/tmp/empty.mhtml"),
            totalBytes = 0,
            contentType = ContentType.MHTML
        ).toList()

        val progress: CopyProgress = results.last().getOrThrow()
        assertEquals(0L, progress.copiedBytes)
        assertEquals(0L, progress.totalBytes)

        val cacheKey = openManager.generateCacheKey(VfsPath.LocalFile("/tmp/empty.mhtml"), ContentType.MHTML, 0)
        val committed = openManager.resolveCacheFile(ContentType.MHTML, cacheKey, "mhtml")
        assertTrue("已知空文件应提交 0 字节成品", committed.exists())
        assertEquals(0L, committed.length())
    }

    @Test
    fun unknownLength_acceptsActualBytesAndReportsNullTotal() = runBlocking {
        val fileSystem = StubFileSystem { Result.success(payloadStream(42)) }
        val openManager = manager(fileSystem)

        val results = openManager.openToCache(
            src = VfsPath.LocalFile("/tmp/unknown.mhtml"),
            totalBytes = null,
            contentType = ContentType.MHTML
        ).toList()

        val progress = results.last().getOrThrow()
        assertEquals(42L, progress.copiedBytes)
        assertNull("未知长度不得伪造 total", progress.totalBytes)

        val cacheKey = openManager.generateCacheKey(VfsPath.LocalFile("/tmp/unknown.mhtml"), ContentType.MHTML, null)
        val committed = openManager.resolveCacheFile(ContentType.MHTML, cacheKey, "mhtml")
        assertTrue(committed.exists())
        assertEquals(42L, committed.length())
    }

    @Test
    fun failedCopy_doesNotDamageOtherValidCache() = runBlocking {
        val keep = File(cacheRoot(), "mhtml/keep/content.mhtml").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(50))
        }
        val fileSystem = StubFileSystem { Result.success(payloadStream(60)) }

        val results = manager(fileSystem).openToCache(
            src = VfsPath.LocalFile("/tmp/failing.mhtml"),
            totalBytes = 100,
            contentType = ContentType.MHTML
        ).toList()

        assertTrue(results.last().isFailure)
        assertTrue("其他持有者的有效缓存不得被删除", keep.exists())
        assertEquals(50L, keep.length())
    }

    @Test(timeout = 60_000)
    fun cancelledCopy_cleansHalfProductAndNeverCommits() = runBlocking {
        val started = CountDownLatch(1)
        val endless = object : InputStream() {
            override fun read(): Int = 'x'.code
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                java.util.Arrays.fill(b, off, off + len, 'x'.code.toByte())
                return len
            }
        }
        val fileSystem = StubFileSystem { Result.success(endless) }
        val openManager = manager(fileSystem)

        val job = launch(Dispatchers.IO) {
            openManager.openToCache(
                src = VfsPath.LocalFile("/tmp/endless.mhtml"),
                totalBytes = null,
                contentType = ContentType.MHTML
            ).collect { started.countDown() }
        }

        assertTrue("拷贝应已开始", started.await(10, TimeUnit.SECONDS))
        job.cancelAndJoin()

        assertTrue("取消后不得残留成品或半成品", cacheArtifacts().isEmpty())
    }

    private class StubFileSystem(
        private val onOpen: (VfsPath) -> Result<InputStream>
    ) : IFileSystem {
        override suspend fun list(dir: VfsPath, offset: Int, limit: Int): Result<List<VfsEntry>> =
            Result.success(emptyList())
        override suspend fun openInputStream(path: VfsPath): Result<InputStream> = onOpen(path)
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
