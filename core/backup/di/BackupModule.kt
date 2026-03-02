package core.backup.di

import core.backup.JsonBackupManager
import core.data.repo.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BackupModule {
    @Provides
    @Singleton
    fun provideJsonBackupManager(
        favoritesRepository: FavoritesRepository,
        historyRepository: HistoryRepository,
        networkConfigRepository: NetworkConfigRepository,
        titleCacheRepository: TitleCacheRepository
    ): JsonBackupManager = JsonBackupManager(
        favoritesRepository,
        historyRepository,
        networkConfigRepository,
        titleCacheRepository
    )
}
