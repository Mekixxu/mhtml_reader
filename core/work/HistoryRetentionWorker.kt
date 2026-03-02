package core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import core.domain.usecase.EnforceHistoryRetentionUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class HistoryRetentionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val enforceUseCase: EnforceHistoryRetentionUseCase
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val maxItems = inputData.getInt("maxItems", 500)
        val maxDays = inputData.getInt("maxDays", 365)
        enforceUseCase.run(maxItems, maxDays)
        return Result.success()
    }
}

