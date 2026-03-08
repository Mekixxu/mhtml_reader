package com.html_reader

import android.content.Context
import core.data.repo.FavoritesRepository
import core.data.repo.NetworkConfigRepository
import core.data.repo.TitleCacheRepository
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
                CoreRuntime.ensure(context)
                val db = CoreRuntime.database
                val dispatcherProvider = CoreRuntime.dispatcherProvider
                val appContext = context.applicationContext
                
                Holder(
                    folderSessionRepository = FolderSessionRepository(db.folderSessionDao(), dispatcherProvider),
                    currentSessionStore = AppCurrentSessionStore(),
                    favoritesRepository = FavoritesRepository(db.favoriteDao(), dispatcherProvider),
                    networkConfigRepository = NetworkConfigRepository(db.networkConfigDao(), dispatcherProvider),
                    titleCacheRepository = TitleCacheRepository(db.titleCacheDao(), dispatcherProvider),
                    sessionSourceStore = AppSessionSourceStore(appContext),
                    authorizedDirStore = AppAuthorizedDirStore(appContext, dispatcherProvider)
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
