package core.reader.usecase

import core.data.repo.HistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 进度保存封装（节流）。
 * 不持有常驻 CoroutineScope，避免 DI 层泄露；由调用方（VM/Controller）传入 scope。
 */
class SaveReadingProgressUseCase(
    private val historyRepo: HistoryRepository
) {
    private val debounceMs = 500L

    fun save(
        scope: CoroutineScope,
        path: String,
        progress: Float,
        pageIndex: Int = -1
    ): Job = scope.launch {
        delay(debounceMs)
        historyRepo.updateProgress(path, progress.coerceIn(0f, 1f), pageIndex)
    }
}

