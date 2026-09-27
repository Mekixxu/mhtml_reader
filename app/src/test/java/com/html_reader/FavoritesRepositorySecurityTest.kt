package com.html_reader

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import core.common.DefaultDispatcherProvider
import core.data.repo.FavoritesRepository
import core.data.repo.HistoryRepository
import core.database.AppDatabase
import core.database.entity.FavoriteEntity
import core.database.entity.HistoryEntity
import core.database.entity.enums.FavoriteType
import core.database.entity.enums.FileType
import core.database.entity.enums.SourceType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FavoritesRepositorySecurityTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: FavoritesRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = FavoritesRepository(db.favoriteDao(), DefaultDispatcherProvider())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun addFile_stripsUrlPasswordAtRepositoryBoundary() = runBlocking {
        val id = repo.addFile(
            parentId = null,
            name = "remote",
            path = "ftp://alice:s3cret@host:21/private/a.mhtml",
            sourceType = SourceType.FTP
        )

        assertEquals("ftp://alice@host:21/private/a.mhtml", db.favoriteDao().getById(id)!!.path)
    }

    @Test
    fun addDirectory_stripsUrlPasswordAtRepositoryBoundary() = runBlocking {
        val id = repo.addDirectory(
            parentId = null,
            name = "remote dir",
            path = "ftp://bob:pw@host:21/private/",
            sourceType = SourceType.FTP
        )

        assertEquals("ftp://bob@host:21/private/", db.favoriteDao().getById(id)!!.path)
    }

    @Test
    fun migrateLegacyCredentials_cleansExistingRows() = runBlocking {
        val legacyId = db.favoriteDao().insert(
            FavoriteEntity(
                parentId = null,
                name = "legacy",
                type = FavoriteType.FILE,
                path = "ftp://old:plaintext@host/a.mhtml",
                sourceType = SourceType.FTP,
                createdAt = 1L
            )
        )

        repo.migrateLegacyCredentialsIfNeeded()

        assertEquals("ftp://old@host/a.mhtml", db.favoriteDao().getById(legacyId)!!.path)
    }

    @Test
    fun historyUpsert_stripsUrlPassword() = runBlocking {
        val historyRepo = HistoryRepository(db.historyDao(), DefaultDispatcherProvider())
        historyRepo.upsert(
            HistoryEntity(
                path = "ftp://user:secret@host/read.mhtml",
                title = "t",
                lastAccess = 1L,
                progress = 0f,
                pageIndex = -1,
                fileType = FileType.MHTML
            )
        )

        val stored = db.historyDao().getByPath("ftp://user@host/read.mhtml")
        assertTrue(stored != null)
        assertEquals(null, db.historyDao().getByPath("ftp://user:secret@host/read.mhtml"))
    }
}
