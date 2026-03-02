package core.reader.di

import core.cache.CacheOpenManager
import core.cache.TabCacheRegistry
import core.common.DispatcherProvider
import core.data.repo.HistoryRepository
import core.reader.pdf.PdfReaderController
import core.reader.pdf.impl.PdfReaderControllerStub
import core.reader.tab.DefaultReaderTabManager
import core.reader.tab.ReaderTabManager
import core.reader.usecase.RestoreReadingPositionUseCase
import core.reader.usecase.SaveReadingProgressUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReaderModule {

    @Provides
    @Singleton
    fun providePdfReaderController(): PdfReaderController = PdfReaderControllerStub()

    @Provides
    @Singleton
    fun provideTabManager(
        cacheOpenManager: CacheOpenManager,
        tabCacheRegistry: TabCacheRegistry,
        historyRepo: HistoryRepository,
        dispatcherProvider: DispatcherProvider
    ): ReaderTabManager = DefaultReaderTabManager(
        cacheOpenManager, tabCacheRegistry, historyRepo, dispatcherProvider
    )

    @Provides
    fun provideRestoreReadingPositionUseCase(historyRepo: HistoryRepository): RestoreReadingPositionUseCase =
        RestoreReadingPositionUseCase(historyRepo)

    @Provides
    fun provideSaveReadingProgressUseCase(historyRepo: HistoryRepository): SaveReadingProgressUseCase =
        SaveReadingProgressUseCase(historyRepo)
}

