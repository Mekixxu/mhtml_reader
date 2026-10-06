package com.html_reader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.html_reader.files.FilesScrollStateStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FilesScrollStateStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun saveThenRestore_matchingPath_returnsState() {
        FilesScrollStateStore.save(context, sessionId = 7L, path = "/deep/remote", position = 12, top = -40)

        val state = FilesScrollStateStore.restore(context, sessionId = 7L, path = "/deep/remote")

        assertEquals(FilesScrollStateStore.ScrollState(position = 12, top = -40), state)
    }

    @Test
    fun restore_mismatchedPathOrSession_returnsNull() {
        FilesScrollStateStore.save(context, sessionId = 7L, path = "/deep/remote", position = 12, top = -40)

        assertNull(FilesScrollStateStore.restore(context, sessionId = 7L, path = "/other"))
        assertNull(FilesScrollStateStore.restore(context, sessionId = 8L, path = "/deep/remote"))
    }

    @Test
    fun restore_withoutSavedState_returnsNull() {
        assertNull(FilesScrollStateStore.restore(context, sessionId = 9L, path = "/any"))
    }

    @Test
    fun clear_removesState() {
        FilesScrollStateStore.save(context, sessionId = 7L, path = "/deep/remote", position = 12, top = -40)
        FilesScrollStateStore.clear(context, sessionId = 7L)

        assertNull(FilesScrollStateStore.restore(context, sessionId = 7L, path = "/deep/remote"))
    }
}
