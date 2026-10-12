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
import core.database.entity.NetworkConfigEntity
import core.database.entity.enums.FavoriteType
import core.database.entity.enums.NetworkProtocol
import core.database.entity.enums.SourceType
import core.security.CredentialCipher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupRoundTripTest {

    private lateinit var db: AppDatabase
    private lateinit var favoritesRepo: FavoritesRepository
    private lateinit var networkRepo: NetworkConfigRepository
    private lateinit var manager: JsonBackupManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dispatchers = DefaultDispatcherProvider()
        favoritesRepo = FavoritesRepository(db.favoriteDao(), dispatchers)
        networkRepo = NetworkConfigRepository(db.networkConfigDao(), dispatchers, CredentialCipher())
        manager = JsonBackupManager(
            favoritesRepo = favoritesRepo,
            historyRepo = HistoryRepository(db.historyDao(), dispatchers),
            networkRepo = networkRepo,
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
    fun directoryFavorites_roundTripPreservesTypePathSourceParentAndCreatedAt() = runBlocking {
        val rootId = favoritesRepo.addFolder(parentId = null, name = "Root")
        val smbDirId = favoritesRepo.addDirectory(
            parentId = rootId,
            name = "smb dir",
            path = "smb://nas/share/dir",
            sourceType = SourceType.SMB
        )
        favoritesRepo.addDirectory(
            parentId = rootId,
            name = "ftp dir",
            path = "ftp://host/pub/",
            sourceType = SourceType.FTP
        )
        favoritesRepo.addDirectory(
            parentId = rootId,
            name = "local dir",
            path = "/storage/emulated/0/Docs",
            sourceType = SourceType.LOCAL
        )
        favoritesRepo.addFile(
            parentId = smbDirId,
            name = "doc.mhtml",
            path = "smb://nas/share/dir/doc.mhtml",
            sourceType = SourceType.SMB
        )

        val before = favoritesRepo.getAll().associateBy { it.name }
        val json = manager.exportAll()

        val result = manager.importAll(json)
        assertTrue(result.isSuccess)

        val after = favoritesRepo.getAll()
        assertEquals(before.size, after.size)

        val newRoot = after.single { it.name == "Root" }
        val newSmbDir = after.single { it.name == "smb dir" }
        assertEquals(FavoriteType.FOLDER, newSmbDir.type)
        assertEquals("smb://nas/share/dir", newSmbDir.path)
        assertEquals(SourceType.SMB, newSmbDir.sourceType)
        assertEquals(newRoot.id, newSmbDir.parentId)
        assertEquals(before.getValue("smb dir").createdAt, newSmbDir.createdAt)

        val ftpDir = after.single { it.name == "ftp dir" }
        assertEquals(FavoriteType.FOLDER, ftpDir.type)
        assertEquals("ftp://host/pub/", ftpDir.path)
        assertEquals(SourceType.FTP, ftpDir.sourceType)
        assertEquals(newRoot.id, ftpDir.parentId)

        val localDir = after.single { it.name == "local dir" }
        assertEquals(FavoriteType.FOLDER, localDir.type)
        assertEquals("/storage/emulated/0/Docs", localDir.path)
        assertEquals(SourceType.LOCAL, localDir.sourceType)
        assertEquals(newRoot.id, localDir.parentId)

        val file = after.single { it.name == "doc.mhtml" }
        assertEquals(FavoriteType.FILE, file.type)
        assertEquals("smb://nas/share/dir/doc.mhtml", file.path)
        assertEquals(SourceType.SMB, file.sourceType)
        assertEquals(newSmbDir.id, file.parentId)
        assertEquals(before.getValue("doc.mhtml").createdAt, file.createdAt)
    }

    @Test
    fun networkEncoding_roundTripKeepsEncodingAndStillOmitsPassword() = runBlocking {
        db.networkConfigDao().insert(
            NetworkConfigEntity(
                id = 0L,
                name = "GBK FTP",
                protocol = NetworkProtocol.FTP,
                host = "example.com",
                port = 21,
                username = "user",
                password = "secret",
                defaultPath = "/pub",
                encoding = "GBK"
            )
        )

        val json = manager.exportAll()
        assertFalse("备份不应导出明文密码", json.contains("secret"))
        assertTrue("备份应包含 encoding", json.contains("GBK"))

        val result = manager.importAll(json)
        assertTrue(result.isSuccess)

        val imported = networkRepo.getAll().single()
        assertEquals("GBK", imported.encoding)
        assertEquals("", imported.password)
        assertEquals("example.com", imported.host)
    }

    @Test
    fun oldBackupWithoutEncoding_defaultsToAuto() = runBlocking {
        val result = manager.importAll(LEGACY_BUNDLE_WITHOUT_ENCODING)
        assertTrue(result.isSuccess)

        val imported = networkRepo.getAll().single()
        assertEquals("FTP", imported.protocol.name)
        assertEquals("Auto", imported.encoding)
        assertNotNull(imported.id)
    }

    private companion object {
        const val LEGACY_BUNDLE_WITHOUT_ENCODING = """
        {
          "schemaVersion": 1,
          "exportedAt": 1,
          "favorites": [],
          "history": [],
          "networkConfigs": [
            {
              "name":"legacy",
              "protocol":"FTP",
              "host":"legacy.example.com",
              "port":21,
              "username":"u",
              "password":"",
              "defaultPath":"/"
            }
          ],
          "titleCache": []
        }
        """
    }
}
