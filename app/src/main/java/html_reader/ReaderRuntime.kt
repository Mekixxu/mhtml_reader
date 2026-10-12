package com.html_reader

import android.content.Context
import core.cache.CacheOpenManager
import core.cache.TabCacheRegistry
import core.data.repo.HistoryRepository
import core.reader.pdf.PdfReaderController
import core.reader.tab.DefaultReaderTabManager
import core.reader.vm.ReaderViewModel
import core.vfs.local.LocalFileSystem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object ReaderRuntime {
    @Volatile
    private var holder: Holder? = null

    /**
     * 应用级 PDF 控制器清理入口：Fragment 视图销毁后仍能完成 renderer/descriptor 关闭。
     */
    private val pdfCleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun closePdfController(controller: PdfReaderController): Job =
        closePdfControllerAfter(previous = null, controller = controller)

    /**
     * 串行化关闭链：等待 previous 完成后再关闭 controller。
     * 供内容切换时保证 close 一定早于下一次 open，避免迟到 close 关闭新 PDF。
     */
    fun closePdfControllerAfter(previous: Job?, controller: PdfReaderController): Job =
        pdfCleanupScope.launch {
            try {
                previous?.join()
                controller.close()
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                // 关闭失败不再阻塞后续清理，资源引用已由 controller 自己置空
            }
        }

    fun viewModel(context: Context): ReaderViewModel = ensure(context).readerViewModel

    fun historyRepository(context: Context): HistoryRepository = ensure(context).historyRepository

    fun tabCacheRegistry(context: Context): TabCacheRegistry = ensure(context).tabCacheRegistry

    private fun ensure(context: Context): Holder {
        val existing = holder
        if (existing != null) {
            return existing
        }
        return synchronized(this) {
            val recheck = holder
            if (recheck != null) {
                recheck
            } else {
                CoreRuntime.ensure(context)
                val db = CoreRuntime.database
                val dispatcherProvider = CoreRuntime.dispatcherProvider
                val appContext = context.applicationContext
                
                val localFileSystem = LocalFileSystem(appContext, dispatcherProvider)
                val cacheRoot = AppMaintenancePolicy.cacheRoot(appContext)
                val cacheEvictor = core.cache.CacheEvictor(
                    cacheRoot = cacheRoot,
                    maxBytes = 2L * 1024 * 1024 * 1024 // 2GB，与 AppSettings.DEFAULT_CACHE_MAX_BYTES 对齐
                )
                val tabCacheRegistry = TabCacheRegistry(cacheRoot)
                val cacheOpenManager = CacheOpenManager(
                    context = appContext,
                    cacheRoot = cacheRoot,
                    fileSystem = localFileSystem,
                    dispatcherProvider = dispatcherProvider,
                    cacheEvictor = cacheEvictor,
                    activeKeysProvider = tabCacheRegistry::activeCacheKeys
                )
                
                val historyRepository = HistoryRepository(db.historyDao(), dispatcherProvider)
                val tabManager = DefaultReaderTabManager(
                    cacheOpenManager = cacheOpenManager,
                    tabCacheRegistry = tabCacheRegistry,
                    historyRepo = historyRepository,
                    dispatcherProvider = dispatcherProvider
                )
                Holder(
                    readerViewModel = ReaderViewModel(tabManager),
                    historyRepository = historyRepository,
                    tabCacheRegistry = tabCacheRegistry
                ).also { holder = it }
            }
        }
    }

    private data class Holder(
        val readerViewModel: ReaderViewModel,
        val historyRepository: HistoryRepository,
        val tabCacheRegistry: TabCacheRegistry
    )
}
