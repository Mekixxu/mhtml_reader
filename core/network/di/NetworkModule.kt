package core.network.di

import core.data.repo.NetworkConfigRepository
import core.common.DispatcherProvider
import core.network.domain.usecase.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun provideCreateNetworkConfigUseCase(
        repo: NetworkConfigRepository,
        dispatcherProvider: DispatcherProvider
    ) = CreateNetworkConfigUseCase(repo, dispatcherProvider)

    @Provides @Singleton
    fun provideUpdateNetworkConfigUseCase(
        repo: NetworkConfigRepository,
        dispatcherProvider: DispatcherProvider
    ) = UpdateNetworkConfigUseCase(repo, dispatcherProvider)

    @Provides @Singleton
    fun provideDeleteNetworkConfigUseCase(
        repo: NetworkConfigRepository,
        dispatcherProvider: DispatcherProvider
    ) = DeleteNetworkConfigUseCase(repo, dispatcherProvider)

    @Provides @Singleton
    fun provideObserveNetworkConfigsUseCase(repo: NetworkConfigRepository) =
        ObserveNetworkConfigsUseCase(repo)

    @Provides @Singleton
    fun provideGetNetworkConfigUseCase(
        repo: NetworkConfigRepository,
        dispatcherProvider: DispatcherProvider
    ) = GetNetworkConfigUseCase(repo, dispatcherProvider)

    @Provides @Singleton
    fun provideTestNetworkConnectionUseCase(dispatcherProvider: DispatcherProvider) =
        TestNetworkConnectionUseCase(dispatcherProvider)
}
