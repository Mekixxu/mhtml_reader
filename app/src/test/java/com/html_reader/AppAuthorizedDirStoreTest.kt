package com.html_reader

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import core.common.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AppAuthorizedDirStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("app_authorized_dirs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun rapidUpsertThenRemove_preservesCallOrder() = runTest {
        val store = AppAuthorizedDirStore(context, TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
        val treeUri = Uri.parse("content://test/tree")

        store.upsert(treeUri, "folder")
        store.remove(treeUri.toString())
        advanceUntilIdle()

        assertTrue(store.getAll().isEmpty())
    }

    @Test
    fun upsert_persistsEntry() = runTest {
        val store = AppAuthorizedDirStore(context, TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
        val treeUri = Uri.parse("content://test/tree")

        store.upsert(treeUri, "folder")
        advanceUntilIdle()

        assertEquals(listOf(AuthorizedDir(treeUri.toString(), "folder")), store.getAll())
    }

    private class TestDispatcherProvider(
        private val dispatcher: CoroutineDispatcher
    ) : DispatcherProvider {
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
        override val main: CoroutineDispatcher = dispatcher
    }
}
