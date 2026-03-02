package core.domain.usecase

import core.data.repo.HistoryRepository

/**
 * 按用户设定自动淘汰历史记录。
 */
class EnforceHistoryRetentionUseCase(
    private val historyRepo: HistoryRepository
) {
    suspend fun run(maxItems: Int, maxDays: Int) {
        historyRepo.enforceRetention(maxItems, maxDays)
    }
}
