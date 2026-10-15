package com.html_reader

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import core.backup.BackupTransactionRunner
import core.backup.JsonBackupManager
import core.common.DefaultDispatcherProvider
import core.data.repo.FavoritesRepository
import core.data.repo.HistoryRepository
import core.data.repo.NetworkConfigRepository
import core.data.repo.TitleCacheRepository
import core.database.AppDatabase
import core.database.entity.enums.SourceType
import core.security.CredentialCipher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupImportValidationTest {

    private lateinit var db: AppDatabase
    private lateinit var favoritesRepo: FavoritesRepository
    private lateinit var manager: JsonBackupManager
    private var transactionRunCount = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dispatchers = DefaultDispatcherProvider()
        favoritesRepo = FavoritesRepository(db.favoriteDao(), dispatchers)
        manager = JsonBackupManager(
            favoritesRepo = favoritesRepo,
            historyRepo = HistoryRepository(db.historyDao(), dispatchers),
            networkRepo = NetworkConfigRepository(db.networkConfigDao(), dispatchers, CredentialCipher()),
            titleCacheRepo = TitleCacheRepository(db.titleCacheDao(), dispatchers),
            transactionRunner = object : BackupTransactionRunner {
                override suspend fun <T> run(block: suspend () -> T): T {
                    transactionRunCount++
                    return db.withTransaction { block() }
                }
            }
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun unknownFavoriteType_isRejectedBeforeClear() = runBlocking {
        seedFavorite()
        val payload = encodeBundle(
            favorites = listOf(
                """{"name":"bad","type":"BOGUS","path":"","sourceType":"LOCAL","createdAt":1}"""
            )
        )

        assertTrue(manager.importAll(payload).isFailure)
        assertEquals(0, transactionRunCount)
        assertEquals(1, favoritesRepo.getAll().size)
        assertEquals("seed", favoritesRepo.getAll().single().name)
    }

    @Test
    fun unknownSourceType_isRejectedBeforeClear() = runBlocking {
        seedFavorite()
        val payload = encodeBundle(
            favorites = listOf(
                """{"name":"bad","type":"FILE","path":"/tmp/a","sourceType":"WEBDAV","createdAt":1}"""
            )
        )

        assertTrue(manager.importAll(payload).isFailure)
        assertEquals(1, favoritesRepo.getAll().size)
    }

    @Test
    fun unknownFileType_isRejectedBeforeClear() = runBlocking {
        seedFavorite()
        val payload = encodeBundle(
            history = listOf(
                """{"path":"/tmp/a","title":"a","lastAccess":1,"progress":0.5,"pageIndex":0,"fileType":"DOCX"}"""
            )
        )

        assertTrue(manager.importAll(payload).isFailure)
        assertEquals(1, favoritesRepo.getAll().size)
    }

    @Test
    fun unknownNetworkProtocol_isRejectedBeforeClear() = runBlocking {
        seedFavorite()
        val payload = encodeBundle(
            networkConfigs = listOf(
                """{"name":"webdav","protocol":"WEBDAV","host":"h","port":443,"username":"u","password":"","defaultPath":"/"}"""
            )
        )

        assertTrue(manager.importAll(payload).isFailure)
        assertEquals(1, favoritesRepo.getAll().size)
    }

    @Test
    fun invalidNetworkPort_isRejectedBeforeClear() = runBlocking {
        seedFavorite()
        val payload = encodeBundle(
            networkConfigs = listOf(
                """{"name":"bad-port","protocol":"FTP","host":"h","port":70000,"username":"u","password":"","defaultPath":"/"}"""
            )
        )

        assertTrue(manager.importAll(payload).isFailure)
        assertEquals(1, favoritesRepo.getAll().size)
    }

    @Test
    fun invalidHistoryProgressAndPageIndex_areRejectedBeforeClear() = runBlocking {
        seedFavorite()
        val payload = encodeBundle(
            history = listOf(
                """{"path":"/tmp/a","title":"a","lastAccess":1,"progress":1.5,"pageIndex":-2,"fileType":"MHTML"}"""
            )
        )

        assertTrue(manager.importAll(payload).isFailure)
        assertEquals(1, favoritesRepo.getAll().size)
    }

    @Test
    fun duplicateFavoriteIds_areRejectedBeforeClear() = runBlocking {
        seedFavorite()
        val payload = encodeBundle(
            favorites = listOf(
                """{"id":7,"name":"a","type":"FOLDER","path":"","sourceType":"LOCAL","createdAt":1}""",
                """{"id":7,"name":"b","type":"FOLDER","path":"","sourceType":"LOCAL","createdAt":2}"""
            )
        )

        assertTrue(manager.importAll(payload).isFailure)
        assertEquals(1, favoritesRepo.getAll().size)
    }

    private suspend fun seedFavorite() {
        favoritesRepo.addFile(
            parentId = null,
            name = "seed",
            path = "/tmp/seed.mhtml",
            sourceType = SourceType.LOCAL
        )
    }

    private fun encodeBundle(
        favorites: List<String> = emptyList(),
        history: List<String> = emptyList(),
        networkConfigs: List<String> = emptyList(),
        titleCache: List<String> = emptyList()
    ): String = """
        {
          "schemaVersion": 1,
          "exportedAt": 1,
          "favorites": [${favorites.joinToString(",")}],
          "history": [${history.joinToString(",")}],
          "networkConfigs": [${networkConfigs.joinToString(",")}],
          "titleCache": [${titleCache.joinToString(",")}]
        }
    """.trimIndent()
}
