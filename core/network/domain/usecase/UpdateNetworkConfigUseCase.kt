package core.network.domain.usecase

import core.network.domain.model.NetworkConfig
import core.network.domain.validator.NetworkConfigValidator
import core.data.repo.NetworkConfigRepository
import core.network.domain.mapper.NetworkConfigMapper
import core.common.DispatcherProvider
import kotlinx.coroutines.withContext

/**
 * 更新网络配置，校验后保存。
 */
class UpdateNetworkConfigUseCase(
    private val repo: NetworkConfigRepository,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend fun update(config: NetworkConfig): Result<Unit> = withContext(dispatcherProvider.io) {
        NetworkConfigValidator.validate(config).fold(
            onSuccess = { validated ->
                repo.update(NetworkConfigMapper.domainToEntity(validated))
                Result.success(Unit)
            },
            onFailure = { Result.failure(it) }
        )
    }
}
