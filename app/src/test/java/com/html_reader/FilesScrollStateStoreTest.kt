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
    fun restore_legacyPositionAddsHeaderOnceAndNewPositionDoesNotShift() {
        val sessionId = 91_234L
        context.getSharedPreferences(FilesScrollStateStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("scroll_path_$sessionId", "/legacy")
            .putInt("scroll_position_$sessionId", 0)
            .putInt("scroll_top_$sessionId", -8)
            .remove("scroll_version_$sessionId")
            .commit()

        assertEquals(
            FilesScrollStateStore.ScrollState(position = 1, top = -8),
            FilesScrollStateStore.restore(context, sessionId, "/legacy", headerCount = 1)
        )

        FilesScrollStateStore.save(context, sessionId, "/legacy", position = 0, top = 0)
        val expectedHeaderPosition = FilesScrollStateStore.ScrollState(position = 0, top = 0)
        assertEquals(expectedHeaderPosition, FilesScrollStateStore.restore(context, sessionId, "/legacy", 1))
        assertEquals(expectedHeaderPosition, FilesScrollStateStore.restore(context, sessionId, "/legacy", 1))
        FilesScrollStateStore.clear(context, sessionId)
    }

    @Test
    fun clear_removesState() {
        FilesScrollStateStore.save(context, sessionId = 7L, path = "/deep/remote", position = 12, top = -40)
        FilesScrollStateStore.clear(context, sessionId = 7L)

        assertNull(FilesScrollStateStore.restore(context, sessionId = 7L, path = "/deep/remote"))
    }
}
