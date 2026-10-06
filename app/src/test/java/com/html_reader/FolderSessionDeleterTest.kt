package com.html_reader

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.html_reader.files.FilesScrollStateStore
import core.common.DefaultDispatcherProvider
import core.database.AppDatabase
import core.session.repo.FolderSessionRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderSessionDeleterTest {

    private lateinit var db: AppDatabase
    private lateinit var sessionRepo: FolderSessionRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sessionRepo = FolderSessionRepository(db.folderSessionDao(), DefaultDispatcherProvider())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun deleteCurrentSession_movesCurrentToRemainingSession() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = sessionRepo.add("first", "/a")
        val second = sessionRepo.add("second", "/b")
        val sourceStore = AppSessionSourceStore(context)
        val currentStore = AppCurrentSessionStore()
        currentStore.set(first)

        FolderSessionDeleter.delete(context, first, sessionRepo, sourceStore, currentStore)

        assertEquals(second, currentStore.get())
        assertEquals(1, sessionRepo.getAll().size)
    }

    @Test
    fun deleteLastSession_clearsCurrent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val only = sessionRepo.add("only", "/a")
        val currentStore = AppCurrentSessionStore()
        currentStore.set(only)

        FolderSessionDeleter.delete(
            context, only, sessionRepo, AppSessionSourceStore(context), currentStore
        )

        assertNull(currentStore.get())
        assertEquals(0, sessionRepo.getAll().size)
    }

    @Test
    fun deleteNonCurrentSession_keepsCurrent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = sessionRepo.add("first", "/a")
        val second = sessionRepo.add("second", "/b")
        val currentStore = AppCurrentSessionStore()
        currentStore.set(first)

        FolderSessionDeleter.delete(
            context, second, sessionRepo, AppSessionSourceStore(context), currentStore
        )

        assertEquals(first, currentStore.get())
    }

    @Test
    fun deleteSession_clearsScrollState() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = sessionRepo.add("session", "/a")
        FilesScrollStateStore.save(context, sessionId = id, path = "/a", position = 5, top = -10)

        FolderSessionDeleter.delete(
            context, id, sessionRepo, AppSessionSourceStore(context), AppCurrentSessionStore()
        )

        assertNull(FilesScrollStateStore.restore(context, sessionId = id, path = "/a"))
    }
}
