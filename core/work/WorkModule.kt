package core.work

import core.domain.usecase.EnforceHistoryRetentionUseCase
import core.domain.usecase.CleanupTitleCacheUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// DELETE THIS FILE.
// 不要在 Hilt module 里手动 new Worker，更不要传 null Context/伪造 WorkerParameters。
// Worker 应由 WorkManager 创建，并通过 HiltWorker + AssistedInject 注入依赖。

@Module
@InstallIn(SingletonComponent::class)
object WorkModule {
    @Provides @Singleton
    fun provideHistoryRetentionWorker(useCase: EnforceHistoryRetentionUseCase): HistoryRetentionWorker =
        HistoryRetentionWorker(null, WorkerParameters(null, null, emptyList(), 0, null), useCase)

    @Provides @Singleton
    fun provideTitleCacheCleanupWorker(useCase: CleanupTitleCacheUseCase): TitleCacheCleanupWorker =
        TitleCacheCleanupWorker(null, WorkerParameters(null, null, emptyList(), 0, null), useCase)
}
