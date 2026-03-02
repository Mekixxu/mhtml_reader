package core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import core.domain.usecase.CleanupTitleCacheUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class TitleCacheCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val cleanupUseCase: CleanupTitleCacheUseCase
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val maxDays = inputData.getInt("maxDaysToKeep", 90)
        cleanupUseCase.run(maxDays)
        return Result.success()
    }
}

