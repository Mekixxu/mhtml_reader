package core.network.domain.usecase

import core.network.domain.model.NetworkConfig
import core.network.domain.validator.NetworkConfigValidator
import core.data.repo.NetworkConfigRepository
import core.network.domain.mapper.NetworkConfigMapper
import core.common.DispatcherProvider
import kotlinx.coroutines.withContext

/**
 * 创建网络配置，应用校验/补默认/规范化。
 */
class CreateNetworkConfigUseCase(
    private val repo: NetworkConfigRepository,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend fun create(config: NetworkConfig): Result<Long> = withContext(dispatcherProvider.io) {
        NetworkConfigValidator.validate(config).fold(
            onSuccess = { validated ->
                val id = repo.add(NetworkConfigMapper.domainToEntity(validated))
                Result.success(id)
            },
            onFailure = { Result.failure(it) }
        )
    }
}
