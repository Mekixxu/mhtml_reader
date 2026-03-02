package core.settings.di

import android.content.Context
import core.settings.repo.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {
    @Provides @Singleton
    fun provideSettingsRepository(context: Context): SettingsRepository = SettingsRepository(context)
}
