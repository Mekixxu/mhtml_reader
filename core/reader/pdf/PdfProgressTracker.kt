package core.reader.pdf

import core.data.repo.HistoryRepository
import core.reader.model.ReaderTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * PDF 页码进度监控：只更新 pageIndex，不污染 progress（保持原值或写 0..1）。
 */
class PdfProgressTracker(
    private val tab: ReaderTab,
    private val historyRepo: HistoryRepository,
    private val coroutineScope: CoroutineScope
) {
    private var job: Job? = null
    private val debounceMs = 500L

    fun listenPage(flow: Flow<Int>) {
        flow.onEach { page ->
            job?.cancel()
            job = coroutineScope.launch {
                delay(debounceMs)
                // progress 传 tab.lastKnownProgress（或 0f），避免写 -1
                historyRepo.updateProgress(tab.sourcePathRaw, tab.lastKnownProgress.coerceIn(0f, 1f), page)
            }
        }.launchIn(coroutineScope)
    }
}

