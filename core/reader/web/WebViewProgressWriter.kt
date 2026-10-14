package core.reader.web

import android.util.Log
import core.data.repo.HistoryRepository
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes history writes and skips stale queued snapshots for the same source.
 * The writer owns only immutable data; it never retains a WebView or Fragment.
 */
class WebViewProgressWriter(
    private val coroutineScope: CoroutineScope,
    private val saveSnapshot: suspend (WebProgressSnapshot) -> Unit
) {
    private val writesMutex = Mutex()
    private var nextSequence = 0L
    private val latestSequenceByPath = ConcurrentHashMap<String, Long>()

    constructor(
        coroutineScope: CoroutineScope,
        historyRepository: HistoryRepository
    ) : this(coroutineScope, { snapshot ->
        historyRepository.saveWebProgress(
            path = snapshot.sourcePathRaw,
            title = snapshot.title,
            fileType = snapshot.fileType,
            progress = snapshot.progress
        )
    })

    fun enqueue(snapshot: WebProgressSnapshot): Job {
        val sequence = synchronized(this) {
            (++nextSequence).also { latestSequenceByPath[snapshot.sourcePathRaw] = it }
        }
        return coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            writesMutex.withLock {
                if (latestSequenceByPath[snapshot.sourcePathRaw] != sequence) {
                    return@withLock
                }
                try {
                    saveSnapshot(snapshot)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Throwable) {
                    Log.w(TAG, "progress_write_failed error=${error.javaClass.simpleName}")
                } finally {
                    latestSequenceByPath.remove(snapshot.sourcePathRaw, sequence)
                }
            }
        }
    }

    suspend fun awaitWrites() {
        writesMutex.withLock { }
    }

    private companion object {
        const val TAG = "WebProgressWriter"
    }
}
