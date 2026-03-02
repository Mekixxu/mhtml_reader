package core.reader.usecase

import core.data.repo.HistoryRepository
import core.reader.model.ReadingPosition

class RestoreReadingPositionUseCase(
    private val historyRepo: HistoryRepository
) {
    suspend fun getPosition(pathRaw: String): ReadingPosition? {
        val hist = historyRepo.getByPath(pathRaw)
        return hist?.let { ReadingPosition(it.progress, it.pageIndex) }
    }
}

