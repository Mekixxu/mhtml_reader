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
