package com.html_reader

import android.content.Context
import core.cache.CacheOpenManager
import core.cache.TabCacheRegistry
import core.data.repo.HistoryRepository
import core.reader.pdf.PdfReaderController
import core.reader.tab.DefaultReaderTabManager
import core.reader.web.WebViewProgressWriter
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
    private val progressWriteScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun closePdfController(controller: PdfReaderController): Job =
        closePdfControllerAfter(previous = null, controller = controller)

    /**
     * 等待先前退出及其捕获的内容任务结束，再关闭 controller。
     * 调用方必须先取消 pendingJobs，并让后续 open 等待此退出任务。
     */
    fun closePdfControllerAfter(
        previous: Job?,
        controller: PdfReaderController,
        pendingJobs: List<Job> = emptyList()
    ): Job =
        pdfCleanupScope.launch {
            try {
                previous?.join()
                pendingJobs.forEach { it.join() }
                controller.close()
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                // 关闭失败不再阻塞后续清理，资源引用已由 controller 自己置空
            }
        }

    fun viewModel(context: Context): ReaderViewModel = ensure(context).readerViewModel

    fun historyRepository(context: Context): HistoryRepository = ensure(context).historyRepository

    fun webProgressWriter(context: Context): WebViewProgressWriter = ensure(context).webProgressWriter

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
                // 保护与租约的唯一来源；淘汰器与清理器共享同一实例，保证保护检查与删除同步
                val tabCacheRegistry = TabCacheRegistry(cacheRoot)
                val cacheEvictor = core.cache.CacheEvictor(
                    cacheRoot = cacheRoot,
                    maxBytes = 2L * 1024 * 1024 * 1024, // 2GB，与 AppSettings.DEFAULT_CACHE_MAX_BYTES 对齐
                    protection = tabCacheRegistry
                )
                val cacheOpenManager = CacheOpenManager(
                    context = appContext,
                    cacheRoot = cacheRoot,
                    fileSystem = localFileSystem,
                    dispatcherProvider = dispatcherProvider,
                    cacheEvictor = cacheEvictor,
                    leaseRegistry = tabCacheRegistry,
                    activeKeysProvider = tabCacheRegistry::activeCacheKeys
                )
                
                val historyRepository = HistoryRepository(db.historyDao(), dispatcherProvider)
                val webProgressWriter = WebViewProgressWriter(progressWriteScope, historyRepository)
                val tabManager = DefaultReaderTabManager(
                    cacheOpenManager = cacheOpenManager,
                    tabCacheRegistry = tabCacheRegistry,
                    historyRepo = historyRepository,
                    dispatcherProvider = dispatcherProvider
                )
                Holder(
                    readerViewModel = ReaderViewModel(tabManager),
                    historyRepository = historyRepository,
                    webProgressWriter = webProgressWriter,
                    tabCacheRegistry = tabCacheRegistry
                ).also { holder = it }
            }
        }
    }

    private data class Holder(
        val readerViewModel: ReaderViewModel,
        val historyRepository: HistoryRepository,
        val webProgressWriter: WebViewProgressWriter,
        val tabCacheRegistry: TabCacheRegistry
    )
}
