package com.html_reader

import android.content.Context
import androidx.room.Room
import core.common.DefaultDispatcherProvider
import core.data.repo.FavoritesRepository
import core.data.repo.NetworkConfigRepository
import core.data.repo.TitleCacheRepository
import core.database.AppDatabase
import core.session.repo.FolderSessionRepository

object FilesRuntime {
    @Volatile
    private var holder: Holder? = null

    fun folderSessionRepository(context: Context): FolderSessionRepository = ensure(context).folderSessionRepository

    fun currentSessionStore(context: Context): AppCurrentSessionStore = ensure(context).currentSessionStore

    fun favoritesRepository(context: Context): FavoritesRepository = ensure(context).favoritesRepository

    fun networkConfigRepository(context: Context): NetworkConfigRepository = ensure(context).networkConfigRepository

    fun sessionSourceStore(context: Context): AppSessionSourceStore = ensure(context).sessionSourceStore

    fun authorizedDirStore(context: Context): AppAuthorizedDirStore = ensure(context).authorizedDirStore

    fun titleCacheRepository(context: Context): TitleCacheRepository = ensure(context).titleCacheRepository

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
                val appContext = context.applicationContext
                val dispatcherProvider = DefaultDispatcherProvider()
                val database = Room.databaseBuilder(appContext, AppDatabase::class.java, "app_database")
                    .fallbackToDestructiveMigration()
                    .build()
                Holder(
                    folderSessionRepository = FolderSessionRepository(database.folderSessionDao(), dispatcherProvider),
                    currentSessionStore = AppCurrentSessionStore(),
                    favoritesRepository = FavoritesRepository(database.favoriteDao(), dispatcherProvider),
                    networkConfigRepository = NetworkConfigRepository(database.networkConfigDao(), dispatcherProvider),
                    titleCacheRepository = TitleCacheRepository(database.titleCacheDao(), dispatcherProvider),
                    sessionSourceStore = AppSessionSourceStore(appContext),
                    authorizedDirStore = AppAuthorizedDirStore(appContext)
                ).also { holder = it }
            }
        }
    }

    private data class Holder(
        val folderSessionRepository: FolderSessionRepository,
        val currentSessionStore: AppCurrentSessionStore,
        val favoritesRepository: FavoritesRepository,
        val networkConfigRepository: NetworkConfigRepository,
        val titleCacheRepository: TitleCacheRepository,
        val sessionSourceStore: AppSessionSourceStore,
        val authorizedDirStore: AppAuthorizedDirStore
    )
}
