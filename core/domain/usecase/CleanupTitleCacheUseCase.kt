package core.domain.usecase

import core.data.repo.TitleCacheRepository

/**
 * 按指定天数自动清理旧TitleCache数据。
 */
class CleanupTitleCacheUseCase(
    private val titleCacheRepo: TitleCacheRepository
) {
    suspend fun run(maxDaysToKeep: Int) {
        val cutoff = System.currentTimeMillis() - maxDaysToKeep * 86400_000L
        titleCacheRepo.deleteOlderThan(cutoff)
    }
}
