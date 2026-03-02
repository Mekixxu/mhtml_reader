package core.favorites.di

import core.data.repo.FavoritesRepository
import core.common.DispatcherProvider
import core.favorites.domain.validator.FavoritesTreeValidator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FavoritesModule {
    @Provides @Singleton
    fun provideFavoritesTreeValidator(
        repo: FavoritesRepository,
        dispatcherProvider: DispatcherProvider
    ): FavoritesTreeValidator = FavoritesTreeValidator(repo, dispatcherProvider)
}
