package core.network.domain.usecase

import core.data.repo.NetworkConfigRepository
import core.common.DispatcherProvider
import kotlinx.coroutines.withContext

class DeleteNetworkConfigUseCase(
    private val repo: NetworkConfigRepository,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend fun delete(id: Long) = withContext(dispatcherProvider.io) { repo.delete(id) }
}
