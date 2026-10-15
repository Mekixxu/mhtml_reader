package com.html_reader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import core.cache.CacheEvictor
import core.cache.CacheOpenManager
import core.cache.CacheProtection
import core.cache.EvictionResult
import core.cache.TabCacheRegistry
import core.cache.model.ContentType
import core.cache.model.CopyProgress
import core.common.AppError
import core.common.DefaultDispatcherProvider
import core.data.repo.HistoryRepository
import core.database.dao.HistoryDao
import core.database.entity.HistoryEntity
import core.database.entity.enums.FileType
import core.reader.model.OpenRequest
import core.reader.model.OpenState
import core.reader.tab.DefaultReaderTabManager
import core.vfs.IFileSystem
import core.vfs.local.LocalFileSystem
import core.vfs.model.VfsEntry
import core.vfs.model.VfsPath
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Q2/Q3 受控并发回归（正式回归，非探针）。
 *
 * 时序全部用 [CompletableDeferred] 屏障建立，不使用 `Thread.sleep` 或概率重跑：
 * - Q2：拷贝完成并挂起在历史查询时，A 的缓存仍受保护，B 无法腾挪，A 最终必 Ready 且文件存在。
 * - Q3：空预算下两份文件在首块前预约，最多一个获准，磁盘总量不超过上限。
 * 另覆盖：去重不泄漏引用、并发重复打开只保留一个引用、关闭后最后一个引用消失才删文件、
 * 取消/失败不泄漏预约与租约、维护清理不误删受保护文件、未知长度分块预算、删除失败报忙碌，
 * 以及 ReaderRuntime 真实接线（共享同一协调对象）。
 */
@RunWith(RobolectricTestRunner::class)
class CacheCoordinationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    // ---------- Q3：并发预约不超过上限 ----------

    @Test(timeout = 60_000)
    fun concurrentReservations_onEmptyBudget_onlyOneGranted() = runBlocking {
        val cacheRoot = File(tempFolder.root, "app_cache")
        val registry = TabCacheRegistry(cacheRoot)
        val evictor = CacheEvictor(cacheRoot, maxBytes = 200, protection = registry)
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val fileSystem = StubFs {
            entered.complete(Unit) // 已通过 reserve（150 已在带宽内登记），此刻尚未写入首块
            gate.await()
            Result.success(ByteArrayInputStream(ByteArray(150)))
        }
        val manager = manager(cacheRoot, fileSystem, evictor, registry)

        val resultsA = mutableListOf<Result<CopyProgress>>()
        val jobA = launch(Dispatchers.IO) {
            manager.openToCache(VfsPath.LocalFile("/tmp/a.mhtml"), 150, ContentType.MHTML)
                .collect { synchronized(resultsA) { resultsA.add(it) } }
        }
        withTimeout(10_000) { entered.await() }

        // B 不同 key，同为 150：若预算未共享，两者首块前都能通过检查 → 实际 300
        val resultsB = manager.openToCache(VfsPath.LocalFile("/tmp/b.mhtml"), 150, ContentType.MHTML).toList()

        gate.complete(Unit)
        jobA.join()

        assertTrue("A 释放后应成功", resultsA.last().isSuccess)
        assertTrue("B 必须因预算被占用而失败", resultsB.last().isFailure)
        assertEquals(AppError.CacheBusy, resultsB.last().exceptionOrNull())

        val total = cacheRoot.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        assertTrue("磁盘总量不得超过预算(200)，实际=$total", total <= 200)
        // 仅 A 的 150 已提交占用（受保护）；B 失败若泄漏预约则总量为 300、连 40 也放不下。
        // 小额探针 READY 即证明 B 的预约已归还。
        assertEquals(EvictionResult.READY, evictor.reserve("probe", 40, emptySet()))
        evictor.releaseReservation("probe")
    }

    // ---------- Q2：历史等待期间缓存受保护 ----------

    @Test(timeout = 60_000)
    fun cacheProtectedWhileAwaitingHistory_thenBecomesReady() = runBlocking {
        val cacheRoot = File(tempFolder.root, "app_cache")
        val registry = TabCacheRegistry(cacheRoot)
        val evictor = CacheEvictor(cacheRoot, maxBytes = 200, protection = registry)
        val dispatchers = DefaultDispatcherProvider()
        val fileSystem = LocalFileSystem(context, dispatchers)
        val manager = manager(cacheRoot, fileSystem, evictor, registry)

        val aFile = File(tempFolder.root, "a.mhtml").apply { writeBytes(ByteArray(150)) }
        val bFile = File(tempFolder.root, "b.mhtml").apply { writeBytes(ByteArray(150)) }

        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val historyRepo = HistoryRepository(BlockingHistoryDao(entered, gate, blockFirstOnly = true), dispatchers)
        val tabManager = DefaultReaderTabManager(manager, registry, historyRepo, dispatchers)

        val statesA = mutableListOf<OpenState>()
        val jobA = launch(Dispatchers.IO) {
            tabManager.openNewTab(request(aFile, "vA")).collect { synchronized(statesA) { statesA.add(it) } }
        }
        // A 已提交 150 字节成品并持有租约，正挂起在历史查询
        withTimeout(10_000) { entered.await() }

        // B 需要 150：唯一腾挪途径是删除 A 的已提交缓存，受保护时必须失败
        val statesB = withTimeout(10_000) { tabManager.openNewTab(request(bFile, "vB")).toList() }

        gate.complete(Unit)
        jobA.join()

        assertTrue("A 最终必须 Ready", statesA.any { it is OpenState.Ready })
        val errorB = statesB.last()
        assertTrue("B 必须失败，不得抢占 A 的缓存", errorB is OpenState.Error)
        assertEquals(AppError.CacheBusy, (errorB as OpenState.Error).error)

        val keyA = manager.generateCacheKey(VfsPath.LocalFile(aFile.absolutePath), ContentType.MHTML, 150, "vA")
        assertTrue("A 的成品必须存在", manager.resolveCacheFile(ContentType.MHTML, keyA, "mhtml").exists())
    }

    // ---------- 去重：不泄漏引用，关闭后最后一个引用消失才删文件 ----------

    @Test(timeout = 60_000)
    fun dedup_reusesTab_andCloseDeletesOnlyWhenNoRefLeft() = runBlocking {
        val cacheRoot = File(tempFolder.root, "app_cache")
        val registry = TabCacheRegistry(cacheRoot)
        val evictor = CacheEvictor(cacheRoot, maxBytes = 10_000, protection = registry)
        val dispatchers = DefaultDispatcherProvider()
        val manager = manager(cacheRoot, LocalFileSystem(context, dispatchers), evictor, registry)
        val tabManager = DefaultReaderTabManager(
            manager, registry, HistoryRepository(NullHistoryDao(), dispatchers), dispatchers
        )

        val file = File(tempFolder.root, "same.mhtml").apply { writeBytes(ByteArray(120)) }
        val req = request(file, "v1")

        val first = tabManager.openNewTab(req).toList()
        val key = manager.generateCacheKey(VfsPath.LocalFile(file.absolutePath), ContentType.MHTML, 120, "v1")
        val committed = manager.resolveCacheFile(ContentType.MHTML, key, "mhtml")
        assertTrue("首次打开必须提交成品", committed.exists())
        assertEquals(setOf(key), registry.activeCacheKeys())
        val tabId = first.filterIsInstance<OpenState.Ready>().single().tab.tabId

        val second = tabManager.openNewTab(req).toList()
        assertEquals(
            "同路径同版本必须复用同一 tab",
            tabId,
            second.filterIsInstance<OpenState.Ready>().single().tab.tabId
        )
        assertTrue("去重不得删除缓存", committed.exists())
        assertEquals("去重不得增加引用", setOf(key), registry.activeCacheKeys())

        tabManager.closeTab(tabId)
        assertFalse("最后一个引用消失后才允许删除缓存", committed.exists())
        assertTrue(registry.activeCacheKeys().isEmpty())
    }

    @Test(timeout = 60_000)
    fun concurrentSameKeyOpen_doesNotLeakExtraReference() = runBlocking {
        val cacheRoot = File(tempFolder.root, "app_cache")
        val registry = TabCacheRegistry(cacheRoot)
        val evictor = CacheEvictor(cacheRoot, maxBytes = 10_000, protection = registry)
        val dispatchers = DefaultDispatcherProvider()
        val manager = manager(cacheRoot, LocalFileSystem(context, dispatchers), evictor, registry)

        // 只阻塞第一次历史查询（A）；B 的查询立即返回，从而 B 先行建 tab，A 落入重复分支释放多余租约
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val historyRepo = HistoryRepository(BlockingHistoryDao(entered, gate, blockFirstOnly = true), dispatchers)
        val tabManager = DefaultReaderTabManager(manager, registry, historyRepo, dispatchers)

        val file = File(tempFolder.root, "shared.mhtml").apply { writeBytes(ByteArray(200)) }
        val req = request(file, "v1")

        val statesA = mutableListOf<OpenState>()
        val jobA = launch(Dispatchers.IO) {
            tabManager.openNewTab(req).collect { synchronized(statesA) { statesA.add(it) } }
        }
        withTimeout(10_000) { entered.await() } // A 已提交并挂起，尚未建 tab

        val statesB = tabManager.openNewTab(req).toList()

        gate.complete(Unit)
        jobA.join()

        assertEquals("同路径同版本只应存在一个 tab", 1, tabManager.observeTabs().value.size)
        assertEquals("并发重复打开不得泄漏额外引用", 1, registry.activeCacheKeys().size)
        assertTrue(statesA.any { it is OpenState.Ready })
        assertTrue(statesB.any { it is OpenState.Ready })
    }

    // ---------- 取消 / 失败不泄漏预约与租约 ----------

    @Test(timeout = 60_000)
    fun cancelDuringCopy_releasesReservationAndLease() = runBlocking {
        val cacheRoot = File(tempFolder.root, "app_cache")
        val registry = TabCacheRegistry(cacheRoot)
        val evictor = CacheEvictor(cacheRoot, maxBytes = 1000, protection = registry)
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val fileSystem = StubFs {
            entered.complete(Unit)
            gate.await()
            Result.success(ByteArrayInputStream(ByteArray(100)))
        }
        val manager = manager(cacheRoot, fileSystem, evictor, registry)

        val job = launch(Dispatchers.IO) {
            manager.openToCache(VfsPath.LocalFile("/tmp/c.mhtml"), 300, ContentType.MHTML).collect { }
        }
        withTimeout(10_000) { entered.await() } // 已预约 300
        job.cancelAndJoin()

        assertTrue("取消后不得残留保护引用", registry.activeCacheKeys().isEmpty())
        // 若 300 预约泄漏，则 900 预约将因超预算失败
        assertEquals(EvictionResult.READY, evictor.reserve("probe", 900, emptySet()))
        evictor.releaseReservation("probe")
    }

    @Test(timeout = 60_000)
    fun unknownLength_respectsBudget_andRecoversAfterFailure() = runBlocking {
        val cacheRoot = File(tempFolder.root, "app_cache")
        val registry = TabCacheRegistry(cacheRoot)
        val evictor = CacheEvictor(cacheRoot, maxBytes = 300, protection = registry)
        val big = StubFs { Result.success(ByteArrayInputStream(ByteArray(400))) }
        val managerBig = manager(cacheRoot, big, evictor, registry)

        val failed = managerBig.openToCache(VfsPath.LocalFile("/tmp/u.mhtml"), null, ContentType.MHTML).toList()
        assertTrue("未知长度超预算必须失败", failed.last().isFailure)
        assertEquals(AppError.CacheLimitExceeded, failed.last().exceptionOrNull())
        assertTrue(
            "失败不得残留成品或半成品",
            cacheRoot.walkTopDown().filter { it.isFile }.none()
        )

        // 预算已归还：较小未知长度仍可成功
        val ok = StubFs { Result.success(ByteArrayInputStream(ByteArray(200))) }
        val managerOk = manager(cacheRoot, ok, evictor, registry)
        val succeeded = managerOk.openToCache(VfsPath.LocalFile("/tmp/u2.mhtml"), null, ContentType.MHTML).toList()
        assertTrue("预算归还后应可继续使用", succeeded.last().isSuccess)
    }

    // ---------- 维护清理 / 删除失败 ----------

    @Test
    fun maintenanceCleanup_doesNotDeleteProtectedFile() = runBlocking {
        val cacheRoot = File(tempFolder.root, "app_cache")
        val registry = TabCacheRegistry(cacheRoot)
        val evictor = CacheEvictor(cacheRoot, maxBytes = 10_000, protection = registry)

        val protectedFile = File(cacheRoot, "mhtml/protected/content.mhtml").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(50))
        }
        val oldFile = File(cacheRoot, "mhtml/old/content.mhtml").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(40))
            setLastModified(0L)
        }
        registry.acquire("protected", "mhtml") // 建立保护（模拟活跃标签/在途）

        evictor.evictOldFiles(maxAgeMs = 0, protectedKeys = emptySet())

        assertTrue("受保护文件不得被清理", protectedFile.exists())
        assertFalse("过期未受保护文件应被清理", oldFile.exists())
    }

    @Test
    fun deleteFailure_isReportedAsCannotFree() = runBlocking {
        val cacheRoot = File(tempFolder.root, "app_cache")
        val old = File(cacheRoot, "mhtml/old/content.mhtml").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(150))
        }
        // 模拟删除失败（句柄占用 / 权限）：返回 false 不算释放空间
        val refusing = object : CacheProtection {
            override fun protectedKeys(): Set<String> = emptySet()
            override fun <T> withProtectionLock(block: () -> T): T = block()
            override fun deleteCacheDirIfUnprotected(cacheKey: String, dir: File): Boolean = false
        }
        val evictor = CacheEvictor(cacheRoot, maxBytes = 200, protection = refusing)

        val result = evictor.makeRoomFor(requiredBytes = 150, protectedKeys = emptySet())

        assertEquals(EvictionResult.CANNOT_FREE, result)
        assertTrue("删除失败时文件必须保留", old.exists())
    }

    // ---------- ReaderRuntime 接线验收（共享同一协调对象） ----------

    @Test(timeout = 60_000)
    fun readerRuntime_sharesSingleRegistryAndProtectsOpenTab() = runBlocking {
        val registry = ReaderRuntime.tabCacheRegistry(context)
        assertSame("registry 必须是单例共享", registry, ReaderRuntime.tabCacheRegistry(context))

        val file = File(tempFolder.root, "wired.mhtml").apply { writeBytes(ByteArray(180)) }
        val vm = ReaderRuntime.viewModel(context)
        val states = vm.open(request(file, "w1")).toList()

        val ready = states.filterIsInstance<OpenState.Ready>().singleOrNull()
        assertTrue("通过 Runtime 打开应进入 Ready", ready != null)
        assertTrue("Runtime 打开后应在同一 registry 上登记保护", registry.activeCacheKeys().isNotEmpty())

        vm.closeTab(ready!!.tab.tabId)
        assertTrue("关闭标签后保护应释放", registry.activeCacheKeys().isEmpty())
    }

    // ---------- helpers ----------

    private fun manager(
        cacheRoot: File,
        fileSystem: IFileSystem,
        evictor: CacheEvictor,
        registry: TabCacheRegistry
    ): CacheOpenManager = CacheOpenManager(
        context = context,
        cacheRoot = cacheRoot,
        fileSystem = fileSystem,
        dispatcherProvider = DefaultDispatcherProvider(),
        cacheEvictor = evictor,
        leaseRegistry = registry,
        activeKeysProvider = registry::activeCacheKeys
    )

    private fun request(file: File, version: String): OpenRequest = OpenRequest(
        source = VfsPath.LocalFile(file.absolutePath),
        fileName = file.name,
        fileType = FileType.MHTML,
        versionStamp = version,
        background = false
    )

    private class StubFs(private val onOpen: suspend (VfsPath) -> Result<InputStream>) : IFileSystem {
        override suspend fun list(dir: VfsPath, offset: Int, limit: Int): Result<List<VfsEntry>> =
            Result.success(emptyList())

        override suspend fun openInputStream(path: VfsPath): Result<InputStream> = onOpen(path)

        override suspend fun openOutputStream(path: VfsPath, append: Boolean): Result<OutputStream> =
            Result.failure(AppError.UnsupportedOperation)

        override suspend fun createFile(parentDir: VfsPath, name: String, mimeType: String?): Result<VfsPath> =
            Result.failure(AppError.UnsupportedOperation)

        override suspend fun exists(path: VfsPath): Result<Boolean> = Result.success(false)

        override suspend fun createFolder(path: VfsPath): Result<Unit> =
            Result.failure(AppError.UnsupportedOperation)

        override suspend fun delete(path: VfsPath): Result<Unit> = Result.failure(AppError.UnsupportedOperation)

        override suspend fun rename(from: VfsPath, toName: String): Result<VfsPath> =
            Result.failure(AppError.UnsupportedOperation)

        override suspend fun move(from: VfsPath, toDir: VfsPath): Result<VfsPath> =
            Result.failure(AppError.UnsupportedOperation)

        override suspend fun copy(from: VfsPath, toDir: VfsPath): Result<VfsPath> =
            Result.failure(AppError.UnsupportedOperation)

        override suspend fun lastModified(path: VfsPath): Result<Long> =
            Result.failure(AppError.UnsupportedOperation)
    }

    /** 阻塞型历史 DAO：用于在「拷贝完成、尚未建 tab」的窗口内制造确定性时序。 */
    private class BlockingHistoryDao(
        private val entered: CompletableDeferred<Unit>,
        private val gate: CompletableDeferred<Unit>,
        private val blockFirstOnly: Boolean
    ) : HistoryDao {
        private val calls = AtomicInteger(0)

        override suspend fun upsert(entity: HistoryEntity) = Unit
        override suspend fun touchOpen(path: String, title: String, lastAccess: Long, fileType: FileType) = Unit
        override suspend fun saveWebProgress(
            path: String, title: String, lastAccess: Long, progress: Float, fileType: FileType
        ) = Unit

        override suspend fun updateProgress(path: String, progress: Float, pageIndex: Int, lastAccess: Long) = Unit
        override fun observeRecent(limit: Int, offset: Int): Flow<List<HistoryEntity>> = flowOf(emptyList())
        override suspend fun getRecent(limit: Int, offset: Int): List<HistoryEntity> = emptyList()

        override suspend fun getByPath(path: String): HistoryEntity? {
            val n = calls.incrementAndGet()
            if (!blockFirstOnly || n == 1) {
                entered.complete(Unit)
                gate.await()
            }
            return null
        }

        override suspend fun delete(path: String) = Unit
        override suspend fun clearAll() = Unit
        override suspend fun deleteOlderThan(epochMs: Long) = Unit
        override suspend fun deleteOldest(keep: Int) = Unit
        override suspend fun getAll(): List<HistoryEntity> = emptyList()
    }

    private class NullHistoryDao : HistoryDao {
        override suspend fun upsert(entity: HistoryEntity) = Unit
        override suspend fun touchOpen(path: String, title: String, lastAccess: Long, fileType: FileType) = Unit
        override suspend fun saveWebProgress(
            path: String, title: String, lastAccess: Long, progress: Float, fileType: FileType
        ) = Unit

        override suspend fun updateProgress(path: String, progress: Float, pageIndex: Int, lastAccess: Long) = Unit
        override fun observeRecent(limit: Int, offset: Int): Flow<List<HistoryEntity>> = flowOf(emptyList())
        override suspend fun getRecent(limit: Int, offset: Int): List<HistoryEntity> = emptyList()
        override suspend fun getByPath(path: String): HistoryEntity? = null
        override suspend fun delete(path: String) = Unit
        override suspend fun clearAll() = Unit
        override suspend fun deleteOlderThan(epochMs: Long) = Unit
        override suspend fun deleteOldest(keep: Int) = Unit
        override suspend fun getAll(): List<HistoryEntity> = emptyList()
    }
}
