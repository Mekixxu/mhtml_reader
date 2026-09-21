package com.html_reader

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import core.database.AppDatabase
import core.database.dao.HistoryDao
import core.database.entity.HistoryEntity
import core.database.entity.enums.FileType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HistoryDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: HistoryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.historyDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun deleteOldest_underLimit_deletesNothing() = runBlocking {
        val base = System.currentTimeMillis()
        insertHistory("old", base - 3_000)
        insertHistory("mid", base - 2_000)
        insertHistory("new", base - 1_000)

        dao.deleteOldest(keep = 10)

        val all = dao.getAll()
        assertEquals(3, all.size)
        assertTrue(all.any { it.path == "old" })
        assertTrue(all.any { it.path == "mid" })
        assertTrue(all.any { it.path == "new" })
    }

    @Test
    fun deleteOldest_overLimit_removesOldestFirst() = runBlocking {
        val base = System.currentTimeMillis()
        insertHistory("old", base - 3_000)
        insertHistory("mid", base - 2_000)
        insertHistory("new", base - 1_000)

        dao.deleteOldest(keep = 2)

        val remaining = dao.getAll().map { it.path }.toSet()
        assertEquals(setOf("mid", "new"), remaining)
        assertFalse(remaining.contains("old"))
    }

    @Test
    fun touchOpen_preservesExistingProgressAndPageIndex() = runBlocking {
        val path = "/docs/read.mhtml"
        dao.upsert(
            HistoryEntity(
                path = path,
                title = "old title",
                lastAccess = 1_000,
                progress = 0.42f,
                pageIndex = 7,
                fileType = FileType.MHTML
            )
        )

        dao.touchOpen(path = path, title = "new title", lastAccess = 2_000, fileType = FileType.MHTML)

        val row = dao.getByPath(path)!!
        assertEquals("new title", row.title)
        assertEquals(2_000, row.lastAccess)
        assertEquals(0.42f, row.progress, 0.0001f)
        assertEquals(7, row.pageIndex)
    }

    @Test
    fun touchOpen_insertsNewRowWhenMissing() = runBlocking {
        dao.touchOpen(path = "/docs/new.pdf", title = "new", lastAccess = 3_000, fileType = FileType.PDF)

        val row = dao.getByPath("/docs/new.pdf")!!
        assertEquals("new", row.title)
        assertEquals(0f, row.progress, 0.0001f)
        assertEquals(-1, row.pageIndex)
    }

    private suspend fun insertHistory(path: String, lastAccess: Long) {
        dao.upsert(
            HistoryEntity(
                path = path,
                title = path,
                lastAccess = lastAccess,
                progress = 0f,
                pageIndex = -1,
                fileType = FileType.MHTML
            )
        )
    }
}
