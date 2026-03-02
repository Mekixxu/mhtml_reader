package core.fileops.di

import core.vfs.IFileSystem
import core.fileops.util.NameConflictResolver
import core.common.DispatcherProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * FileOps DI-Module
 */
@Module
@InstallIn(SingletonComponent::class)
object FileOpsModule {
    @Provides @Singleton
    fun provideNameConflictResolver(
        fileSystem: IFileSystem, dispatcherProvider: DispatcherProvider
    ): NameConflictResolver = NameConflictResolver(fileSystem, dispatcherProvider)
}
