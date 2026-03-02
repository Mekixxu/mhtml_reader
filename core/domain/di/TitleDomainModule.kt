package core.domain.di

import core.cache.CacheOpenManager
import core.common.DispatcherProvider
import core.data.repo.HistoryRepository
import core.data.repo.TitleCacheRepository
import core.domain.usecase.CleanupTitleCacheUseCase
import core.domain.usecase.EnforceHistoryRetentionUseCase
import core.domain.usecase.ObserveDisplayTitlesUseCase
import core.title.TitleExtractor
import core.title.TitleExtractorFacade
import core.title.impl.HtmlTitleExtractor
import core.title.impl.PdfTitleExtractor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TitleDomainModule {

    @Provides
    @Singleton
    fun provideHtmlTitleExtractor(dispatcherProvider: DispatcherProvider): HtmlTitleExtractor =
        HtmlTitleExtractor(dispatcherProvider)

    @Provides
    @Singleton
    fun providePdfTitleExtractor(dispatcherProvider: DispatcherProvider): PdfTitleExtractor =
        PdfTitleExtractor(dispatcherProvider)

    @Provides
    @Singleton
    fun provideTitleExtractor(
        html: HtmlTitleExtractor,
        pdf: PdfTitleExtractor
    ): TitleExtractor = TitleExtractorFacade(html, pdf)

    @Provides
    @Singleton
    fun provideObserveDisplayTitlesUseCase(
        cacheOpenManager: CacheOpenManager,
        titleCacheRepo: TitleCacheRepository,
        titleExtractor: TitleExtractor,
        dispatcherProvider: DispatcherProvider
    ): ObserveDisplayTitlesUseCase =
        ObserveDisplayTitlesUseCase(cacheOpenManager, titleCacheRepo, titleExtractor, dispatcherProvider)

    @Provides
    @Singleton
    fun provideEnforceHistoryRetentionUseCase(historyRepo: HistoryRepository): EnforceHistoryRetentionUseCase =
        EnforceHistoryRetentionUseCase(historyRepo)

    @Provides
    @Singleton
    fun provideCleanupTitleCacheUseCase(titleCacheRepo: TitleCacheRepository): CleanupTitleCacheUseCase =
        CleanupTitleCacheUseCase(titleCacheRepo)
}

