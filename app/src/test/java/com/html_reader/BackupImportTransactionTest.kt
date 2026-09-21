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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupImportTransactionTest {

    private lateinit var db: AppDatabase
    private lateinit var favoritesRepo: FavoritesRepository
    private lateinit var manager: JsonBackupManager

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
                override suspend fun <T> run(block: suspend () -> T): T =
                    db.withTransaction { block() }
            }
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun importAll_failureMidway_rollsBackClearedData() = runBlocking {
        favoritesRepo.addFile(
            parentId = null,
            name = "seed",
            path = "/tmp/seed.mhtml",
            sourceType = SourceType.LOCAL
        )

        // 两条完全相同的网络配置触发唯一索引冲突，导入中途失败
        val result = manager.importAll(FAILING_BUNDLE)

        assertTrue(result.isFailure)
        val favorites = favoritesRepo.getAll()
        assertTrue("原收藏应被事务回滚保留", favorites.any { it.name == "seed" })
        assertFalse("失败导入不应留下部分数据", favorites.any { it.name == "keep-me" })
        assertTrue(db.networkConfigDao().getAll().isEmpty())
        assertTrue(db.historyDao().getAll().isEmpty())
    }

    @Test
    fun importAll_success_replacesData() = runBlocking {
        favoritesRepo.addFile(
            parentId = null,
            name = "seed",
            path = "/tmp/seed.mhtml",
            sourceType = SourceType.LOCAL
        )

        val result = manager.importAll(SUCCESS_BUNDLE)

        assertTrue(result.isSuccess)
        val favorites = favoritesRepo.getAll()
        assertTrue(favorites.any { it.name == "keep-me" })
        assertFalse(favorites.any { it.name == "seed" })
    }

    private companion object {
        const val FAILING_BUNDLE = """
        {
          "schemaVersion": 1,
          "exportedAt": 1,
          "favorites": [
            {"name":"keep-me","type":"FILE","path":"/tmp/keep.mhtml","sourceType":"LOCAL","createdAt":1}
          ],
          "history": [],
          "networkConfigs": [
            {"name":"dup1","protocol":"FTP","host":"h","port":21,"username":"u","password":"","defaultPath":"/"},
            {"name":"dup2","protocol":"FTP","host":"h","port":21,"username":"u","password":"","defaultPath":"/"}
          ],
          "titleCache": []
        }
        """

        const val SUCCESS_BUNDLE = """
        {
          "schemaVersion": 1,
          "exportedAt": 1,
          "favorites": [
            {"name":"keep-me","type":"FILE","path":"/tmp/keep.mhtml","sourceType":"LOCAL","createdAt":1}
          ],
          "history": [],
          "networkConfigs": [],
          "titleCache": []
        }
        """
    }
}
