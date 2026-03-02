package core.maintenance.di

import core.maintenance.DefaultMaintenanceManager
import core.maintenance.MaintenanceManager
import core.settings.repo.SettingsRepository
import core.cache.OrphanCacheCleaner
import core.cache.CacheEvictor
import core.data.repo.HistoryRepository
import core.data.repo.TitleCacheRepository
import core.common.DispatcherProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MaintenanceModule {
    @Provides @Singleton
    fun provideMaintenanceManager(
        settingsRepo: SettingsRepository,
        orphanCleaner: OrphanCacheCleaner,
        cacheEvictor: CacheEvictor,
        historyRepo: HistoryRepository,
        titleCacheRepo: TitleCacheRepository,
        dispatcherProvider: DispatcherProvider
    ): MaintenanceManager = DefaultMaintenanceManager(
        settingsRepo,
        orphanCleaner,
        cacheEvictor,
        historyRepo,
        titleCacheRepo,
        dispatcherProvider
    )
}
