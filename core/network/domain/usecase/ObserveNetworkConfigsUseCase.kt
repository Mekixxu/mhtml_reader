package core.network.domain.usecase

import core.data.repo.NetworkConfigRepository
import core.network.domain.model.NetworkConfig
import core.network.domain.mapper.NetworkConfigMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 观察所有网络配置项、集成domain转换。
 */
class ObserveNetworkConfigsUseCase(
    private val repo: NetworkConfigRepository
) {
    fun observe(): Flow<List<NetworkConfig>> =
        repo.observeAll().map { list -> list.map(NetworkConfigMapper::entityToDomain) }
}
