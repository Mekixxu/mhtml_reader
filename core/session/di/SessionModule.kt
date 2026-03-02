package core.session.di

import core.session.dao.FolderSessionDao
import core.session.repo.FolderSessionRepository
import core.common.DispatcherProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SessionModule {
    @Provides @Singleton
    fun provideFolderSessionRepository(
        dao: FolderSessionDao,
        dispatcherProvider: DispatcherProvider
    ): FolderSessionRepository = FolderSessionRepository(dao, dispatcherProvider)
}
