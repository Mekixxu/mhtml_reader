package core.di

import android.content.Context
import core.cache.CacheEvictor
import core.cache.CacheOpenManager
import core.cache.OrphanCacheCleaner
import core.cache.TabCacheRegistry
import core.common.DefaultDispatcherProvider
import core.common.DispatcherProvider
import core.vfs.local.LocalFileSystem
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.File
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
    fun provideCacheRoot(context: Context): File = File(context.cacheDir, "app_cache")

    @Provides
    @Singleton
    fun provideCacheEvictor(cacheRoot: File): CacheEvictor = CacheEvictor(
        cacheRoot = cacheRoot,
        maxBytes = core.settings.model.AppSettings.DEFAULT_CACHE_MAX_BYTES
    )

    @Provides
    @Singleton
    fun provideTabCacheRegistry(cacheRoot: File): TabCacheRegistry = TabCacheRegistry(cacheRoot)

    @Provides
    @Singleton
    fun provideOrphanCacheCleaner(cacheRoot: File): OrphanCacheCleaner = OrphanCacheCleaner(
        cacheRoot = cacheRoot,
        daysUnused = 3
    )

    @Provides
    @Singleton
    fun provideCacheOpenManager(
        context: Context,
        cacheRoot: File,
        localFileSystem: LocalFileSystem,
        dispatcherProvider: DispatcherProvider,
        cacheEvictor: CacheEvictor
    ): CacheOpenManager = CacheOpenManager(
        context = context,
        cacheRoot = cacheRoot,
        fileSystem = localFileSystem,
        dispatcherProvider = dispatcherProvider,
        cacheEvictor = cacheEvictor
    )
}
