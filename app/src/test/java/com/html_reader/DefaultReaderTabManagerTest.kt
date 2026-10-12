package com.html_reader

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import core.cache.CacheEvictor
import core.cache.CacheOpenManager
import core.cache.TabCacheRegistry
import core.common.AppError
import core.common.DefaultDispatcherProvider
import core.data.repo.HistoryRepository
import core.database.AppDatabase
import core.database.entity.enums.FileType
import core.reader.model.OpenRequest
import core.reader.model.OpenState
import core.reader.tab.DefaultReaderTabManager
import core.vfs.local.LocalFileSystem
import core.vfs.model.VfsPath
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class DefaultReaderTabManagerTest {

    private lateinit var db: AppDatabase
    private lateinit var tabManager: DefaultReaderTabManager
    private lateinit var cacheRoot: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        cacheRoot = File(context.cacheDir, "app_cache_test").apply { mkdirs() }
        val dispatchers = DefaultDispatcherProvider()
        val fileSystem = LocalFileSystem(context, dispatchers)
        val cacheEvictor = CacheEvictor(cacheRoot, maxBytes = 2L * 1024 * 1024 * 1024)
        val cacheOpenManager = CacheOpenManager(context, cacheRoot, fileSystem, dispatchers, cacheEvictor)
        tabManager = DefaultReaderTabManager(
            cacheOpenManager = cacheOpenManager,
            tabCacheRegistry = TabCacheRegistry(cacheRoot),
            historyRepo = HistoryRepository(db.historyDao(), dispatchers),
            dispatcherProvider = dispatchers
        )
    }

    @After
    fun tearDown() {
        cacheRoot.deleteRecursively()
        db.close()
    }

    @Test
    fun openNewTab_deduplicatesSamePathAndVersion() = runBlocking {
        val source = File(cacheRoot.parentFile, "same.mhtml").apply { writeText("<html>same</html>") }
        val request = OpenRequest(
            source = VfsPath.LocalFile(source.absolutePath),
            fileName = source.name,
            fileType = FileType.MHTML,
            versionStamp = "${source.lastModified()}:${source.length()}",
            background = false
        )

        val firstStates = tabManager.openNewTab(request).toList()
        val secondStates = tabManager.openNewTab(request).toList()

        val firstReady = firstStates.filterIsInstance<OpenState.Ready>().single()
        val secondReady = secondStates.filterIsInstance<OpenState.Ready>().single()
        assertEquals(firstReady.tab.tabId, secondReady.tab.tabId)
        assertEquals(1, tabManager.observeTabs().value.size)
        assertTrue(secondStates.any { it is OpenState.Ready })
    }

    @Test
    fun openNewTab_preservesCacheLimitErrorType() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dispatchers = DefaultDispatcherProvider()
        val smallRoot = File(context.cacheDir, "app_cache_limit_${System.nanoTime()}").apply { mkdirs() }
        try {
            val fileSystem = LocalFileSystem(context, dispatchers)
            val openManager = CacheOpenManager(
                context = context,
                cacheRoot = smallRoot,
                fileSystem = fileSystem,
                dispatcherProvider = dispatchers,
                cacheEvictor = CacheEvictor(smallRoot, maxBytes = 50)
            )
            val manager = DefaultReaderTabManager(
                cacheOpenManager = openManager,
                tabCacheRegistry = TabCacheRegistry(smallRoot),
                historyRepo = HistoryRepository(db.historyDao(), dispatchers),
                dispatcherProvider = dispatchers
            )
            val source = File(cacheRoot.parentFile, "too-big.mhtml").apply { writeBytes(ByteArray(100)) }
            val states = manager.openNewTab(
                OpenRequest(
                    source = VfsPath.LocalFile(source.absolutePath),
                    fileName = source.name,
                    fileType = FileType.MHTML,
                    versionStamp = "${source.lastModified()}:${source.length()}"
                )
            ).toList()

            val error = states.filterIsInstance<OpenState.Error>().single().error
            assertTrue("UI 必须收到具体容量超限错误", error === AppError.CacheLimitExceeded)
        } finally {
            smallRoot.deleteRecursively()
        }
    }

    @Test
    fun openNewTab_preservesCacheBusyErrorType() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dispatchers = DefaultDispatcherProvider()
        val smallRoot = File(context.cacheDir, "app_cache_busy_${System.nanoTime()}").apply { mkdirs() }
        try {
            File(smallRoot, "mhtml/keep/content.mhtml").apply {
                parentFile?.mkdirs()
                writeBytes(ByteArray(400))
            }
            val fileSystem = LocalFileSystem(context, dispatchers)
            val openManager = CacheOpenManager(
                context = context,
                cacheRoot = smallRoot,
                fileSystem = fileSystem,
                dispatcherProvider = dispatchers,
                cacheEvictor = CacheEvictor(smallRoot, maxBytes = 500),
                activeKeysProvider = { setOf("keep") }
            )
            val manager = DefaultReaderTabManager(
                cacheOpenManager = openManager,
                tabCacheRegistry = TabCacheRegistry(smallRoot),
                historyRepo = HistoryRepository(db.historyDao(), dispatchers),
                dispatcherProvider = dispatchers
            )
            val source = File(cacheRoot.parentFile, "needs-room.mhtml").apply { writeBytes(ByteArray(300)) }
            val states = manager.openNewTab(
                OpenRequest(
                    source = VfsPath.LocalFile(source.absolutePath),
                    fileName = source.name,
                    fileType = FileType.MHTML,
                    versionStamp = "${source.lastModified()}:${source.length()}"
                )
            ).toList()

            val error = states.filterIsInstance<OpenState.Error>().single().error
            assertTrue("UI 必须收到具体缓存占用错误", error === AppError.CacheBusy)
        } finally {
            smallRoot.deleteRecursively()
        }
    }
}
