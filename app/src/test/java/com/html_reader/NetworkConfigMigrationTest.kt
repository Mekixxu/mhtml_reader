package com.html_reader

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import core.common.DefaultDispatcherProvider
import core.data.repo.NetworkConfigRepository
import core.database.AppDatabase
import core.database.dao.NetworkConfigDao
import core.database.entity.NetworkConfigEntity
import core.database.entity.enums.NetworkProtocol
import core.security.CredentialCipher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NetworkConfigMigrationTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: NetworkConfigDao
    private lateinit var repo: NetworkConfigRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.networkConfigDao()
        repo = NetworkConfigRepository(dao, DefaultDispatcherProvider(), CredentialCipher())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun migration_updatesOnlyPasswordColumnWhenCasMatches() = runBlocking {
        val original = entity(name = "Original", host = "h1", password = "plain", encoding = "GBK")
        val id = dao.insert(original)

        repo.migrateLegacyPlaintextIfNeeded(
            isEncrypted = { false },
            encrypt = { "ENC:$it" }
        )

        val migrated = dao.getById(id)!!
        assertEquals("ENC:plain", migrated.password)
        assertEquals(original.name, migrated.name)
        assertEquals(original.protocol, migrated.protocol)
        assertEquals(original.host, migrated.host)
        assertEquals(original.port, migrated.port)
        assertEquals(original.username, migrated.username)
        assertEquals(original.defaultPath, migrated.defaultPath)
        assertEquals(original.encoding, migrated.encoding)
    }

    @Test
    fun migration_doesNotOverwritePasswordChangedAfterRead() = runBlocking {
        val id = dao.insert(entity(name = "Before", host = "h2", password = "old", defaultPath = "/before"))
        val storedRow = dao.getById(id)!!

        repo.migrateLegacyPlaintextIfNeeded(
            isEncrypted = { false },
            encrypt = { stored ->
                // 模拟迁移读取旧行之后、CAS 之前用户保存了一个新配置。
                dao.update(
                    storedRow.copy(
                        password = "new",
                        name = "After",
                        defaultPath = "/after"
                    )
                )
                "ENC:$stored"
            }
        )

        val current = dao.getById(id)!!
        assertEquals("new", current.password)
        assertEquals("After", current.name)
        assertEquals("/after", current.defaultPath)
    }

    @Test
    fun migration_updatesPasswordWhenOnlyOtherColumnsChanged() = runBlocking {
        val id = dao.insert(entity(name = "Original", host = "h2b", password = "old", defaultPath = "/before"))
        val storedRow = dao.getById(id)!!
        dao.update(storedRow.copy(name = "Edited", defaultPath = "/after"))

        repo.migrateLegacyPlaintextIfNeeded(
            isEncrypted = { false },
            encrypt = { "ENC:$it" }
        )

        val current = dao.getById(id)!!
        assertEquals("ENC:old", current.password)
        assertEquals("Edited", current.name)
        assertEquals("/after", current.defaultPath)
    }

    @Test
    fun migration_isIdempotent() = runBlocking {
        val id = dao.insert(entity(host = "h3", password = "old"))
        val isEncrypted: (String) -> Boolean = { it.startsWith("ENC:") }
        val encrypt: suspend (String) -> String = { "ENC:$it" }

        repo.migrateLegacyPlaintextIfNeeded(isEncrypted, encrypt)
        repo.migrateLegacyPlaintextIfNeeded(isEncrypted, encrypt)

        assertEquals("ENC:old", dao.getById(id)!!.password)
    }

    @Test
    fun migration_skipsUndecryptableCiphertext() = runBlocking {
        val id = dao.insert(entity(host = "h4", password = "enc:v1:broken"))

        repo.migrateLegacyPlaintextIfNeeded(
            isEncrypted = { it == "enc:v1:broken" },
            encrypt = { error("must not re-encrypt undecryptable ciphertext") }
        )

        assertEquals("enc:v1:broken", dao.getById(id)!!.password)
    }

    @Test
    fun migration_skipsEmptyPassword() = runBlocking {
        val id = dao.insert(entity(host = "h5", password = ""))

        repo.migrateLegacyPlaintextIfNeeded(
            isEncrypted = { error("empty password should be skipped before isEncrypted") },
            encrypt = { error("empty password must not be encrypted") }
        )

        assertEquals("", dao.getById(id)!!.password)
    }

    private fun entity(
        name: String = "cfg",
        host: String,
        password: String,
        defaultPath: String = "/",
        encoding: String = "Auto"
    ) = NetworkConfigEntity(
        id = 0L,
        name = name,
        protocol = NetworkProtocol.FTP,
        host = host,
        port = 21,
        username = "user",
        password = password,
        defaultPath = defaultPath,
        encoding = encoding
    )
}
