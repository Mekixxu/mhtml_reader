package core.di

import android.content.Context
import core.cache.CacheOpenManager
import core.common.DefaultDispatcherProvider
import core.common.DispatcherProvider
import core.vfs.local.LocalFileSystem
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * DI 提供基础依赖
 */
@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()

    @Provides
    @Singleton
    fun provideLocalFileSystem(
        context: Context,
        dispatcherProvider: DispatcherProvider
    ): LocalFileSystem = LocalFileSystem(context, dispatcherProvider)

    @Provides
    @Singleton
    fun provideCacheOpenManager(
        context: Context,
        localFileSystem: LocalFileSystem,
        dispatcherProvider: DispatcherProvider
    ): CacheOpenManager {
        val cacheDir = context.cacheDir.resolve("app_cache")
        return CacheOpenManager(context, cacheDir, localFileSystem, dispatcherProvider)
    }
}
