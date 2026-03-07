package core.network.domain.usecase

import core.data.repo.NetworkConfigRepository
import core.network.domain.model.NetworkConfig
import core.network.domain.mapper.NetworkConfigMapper
import core.common.DispatcherProvider
import kotlinx.coroutines.withContext

class GetNetworkConfigUseCase(
    private val repo: NetworkConfigRepository,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend fun getById(id: Long): NetworkConfig? = withContext(dispatcherProvider.io) {
        repo.getById(id)?.let(NetworkConfigMapper::entityToDomain)
    }
}
