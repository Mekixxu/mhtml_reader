package com.html_reader

import core.reader.web.WebProgressSnapshot
import core.reader.web.WebViewProgressWriter
import core.database.entity.enums.FileType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class WebViewProgressWriterTest {

    @Test
    fun queuedSnapshotsForSameSourceCannotWriteOutOfOrder() = runTest {
        val firstWriteStarted = CompletableDeferred<Unit>()
        val releaseFirstWrite = CompletableDeferred<Unit>()
        val writes = mutableListOf<Pair<String, Float>>()
        val writer = WebViewProgressWriter(this) { snapshot ->
            if (snapshot.sourcePathRaw == "/blocker") {
                firstWriteStarted.complete(Unit)
                releaseFirstWrite.await()
            }
            writes += snapshot.sourcePathRaw to snapshot.progress
        }

        writer.enqueue(snapshot("/blocker", 0.1f))
        runCurrent()
        firstWriteStarted.await()
        writer.enqueue(snapshot("/docs/a.mhtml", 0.2f))
        writer.enqueue(snapshot("/docs/a.mhtml", 0.8f))
        runCurrent()
        releaseFirstWrite.complete(Unit)
        writer.awaitWrites()
        advanceUntilIdle()

        assertEquals(
            listOf("/blocker" to 0.1f, "/docs/a.mhtml" to 0.8f),
            writes
        )
    }

    @Test
    fun newerSnapshotWritesAfterAnOlderInFlightSnapshot() = runTest {
        val firstWriteStarted = CompletableDeferred<Unit>()
        val releaseFirstWrite = CompletableDeferred<Unit>()
        val writes = mutableListOf<Float>()
        val writer = WebViewProgressWriter(this) { snapshot ->
            if (snapshot.progress == 0.2f) {
                firstWriteStarted.complete(Unit)
                releaseFirstWrite.await()
            }
            writes += snapshot.progress
        }

        writer.enqueue(snapshot("/docs/a.mhtml", 0.2f))
        firstWriteStarted.await()
        writer.enqueue(snapshot("/docs/a.mhtml", 0.9f))
        releaseFirstWrite.complete(Unit)
        writer.awaitWrites()

        assertEquals(listOf(0.2f, 0.9f), writes)
    }

    @Test
    fun writesForDifferentSourcesAreSerialized() = runTest {
        var activeWrites = 0
        var maxActiveWrites = 0
        val writer = WebViewProgressWriter(this) {
            activeWrites++
            maxActiveWrites = maxOf(maxActiveWrites, activeWrites)
            kotlinx.coroutines.yield()
            activeWrites--
        }

        writer.enqueue(snapshot("/docs/a.mhtml", 0.2f))
        writer.enqueue(snapshot("/docs/b.mhtml", 0.7f))
        writer.awaitWrites()
        advanceUntilIdle()

        assertEquals(1, maxActiveWrites)
        assertEquals(0, activeWrites)
    }

    private fun snapshot(path: String, progress: Float) =
        WebProgressSnapshot(
            sourcePathRaw = path,
            title = path.substringAfterLast('/'),
            fileType = FileType.MHTML,
            progress = progress
        )
}
