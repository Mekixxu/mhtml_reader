package com.html_reader

import android.content.Context
import core.cache.CacheOpenManager
import core.cache.TabCacheRegistry
import core.data.repo.HistoryRepository
import core.reader.tab.DefaultReaderTabManager
import core.reader.vm.ReaderViewModel
import core.vfs.local.LocalFileSystem

object ReaderRuntime {
    @Volatile
    private var holder: Holder? = null

    fun viewModel(context: Context): ReaderViewModel = ensure(context).readerViewModel

    fun historyRepository(context: Context): HistoryRepository = ensure(context).historyRepository

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
                val cacheRoot = appContext.cacheDir.resolve("app_cache")
                val cacheEvictor = core.cache.CacheEvictor(
                    cacheRoot = cacheRoot,
                    maxBytes = 15L * 1024 * 1024 * 1024 // 15GB
                )
                val cacheOpenManager = CacheOpenManager(
                    context = appContext,
                    cacheRoot = cacheRoot,
                    fileSystem = localFileSystem,
                    dispatcherProvider = dispatcherProvider,
                    cacheEvictor = cacheEvictor
                )
                val tabCacheRegistry = TabCacheRegistry(cacheRoot)
                
                val historyRepository = HistoryRepository(db.historyDao(), dispatcherProvider)
                val tabManager = DefaultReaderTabManager(
                    cacheOpenManager = cacheOpenManager,
                    tabCacheRegistry = tabCacheRegistry,
                    historyRepo = historyRepository,
                    dispatcherProvider = dispatcherProvider
                )
                Holder(
                    readerViewModel = ReaderViewModel(tabManager),
                    historyRepository = historyRepository
                ).also { holder = it }
            }
        }
    }

    private data class Holder(
        val readerViewModel: ReaderViewModel,
        val historyRepository: HistoryRepository
    )
}
