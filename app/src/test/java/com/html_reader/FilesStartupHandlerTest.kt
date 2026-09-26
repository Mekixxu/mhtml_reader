package com.html_reader

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.html_reader.files.FilesStartupHandler
import com.html_reader.files.InitialOpenState
import core.common.DefaultDispatcherProvider
import core.data.repo.NetworkConfigRepository
import core.database.AppDatabase
import core.database.entity.NetworkConfigEntity
import core.database.entity.enums.NetworkProtocol
import core.security.CredentialCipher
import core.session.repo.FolderSessionRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FilesStartupHandlerTest {

    private lateinit var db: AppDatabase
    private lateinit var networkRepo: NetworkConfigRepository
    private lateinit var sessionRepo: FolderSessionRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dispatchers = DefaultDispatcherProvider()
        networkRepo = NetworkConfigRepository(db.networkConfigDao(), dispatchers, CredentialCipher())
        sessionRepo = FolderSessionRepository(db.folderSessionDao(), dispatchers)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun applyInitialOpen_undecryptableCredential_blocksNetworkSession() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configId = db.networkConfigDao().insert(
            NetworkConfigEntity(
                name = "broken",
                protocol = NetworkProtocol.FTP,
                host = "host",
                port = 21,
                username = "user",
                password = "enc:v1:not-valid-payload", // 解密必定失败
                defaultPath = "/"
            )
        )

        var credentialUnavailable = false
        var invalidPath = false
        val currentSessionStore = AppCurrentSessionStore()
        FilesStartupHandler.applyInitialOpen(
            state = InitialOpenState(networkConfigId = configId, startPath = null, safTreeUri = null),
            requestedNetworkEntry = false,
            networkConfigRepository = networkRepo,
            folderSessionRepository = sessionRepo,
            sessionSourceStore = AppSessionSourceStore(context),
            currentSessionStore = currentSessionStore,
            onInvalidStartPath = { invalidPath = true },
            onCredentialUnavailable = { credentialUnavailable = true }
        )

        assertTrue("应提示凭据不可用", credentialUnavailable)
        assertEquals("不应提示无效路径", false, invalidPath)
        assertTrue("不应建立网络会话", sessionRepo.getAll().isEmpty())
        assertNull("不应切换当前会话", currentSessionStore.get())
    }

    @Test
    fun applyInitialOpen_reenterSameNetworkConfig_reusesExistingSession() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configId = db.networkConfigDao().insert(
            NetworkConfigEntity(
                name = "reuse",
                protocol = NetworkProtocol.FTP,
                host = "host",
                port = 21,
                username = "user",
                password = "plain",
                defaultPath = "/"
            )
        )
        val sourceStore = AppSessionSourceStore(context)
        val sessionStore = AppCurrentSessionStore()

        FilesStartupHandler.applyInitialOpen(
            state = InitialOpenState(networkConfigId = configId, startPath = null, safTreeUri = null),
            requestedNetworkEntry = false,
            networkConfigRepository = networkRepo,
            folderSessionRepository = sessionRepo,
            sessionSourceStore = sourceStore,
            currentSessionStore = sessionStore,
            onInvalidStartPath = {}
        )
        val firstSessionId = sessionStore.get()!!
        sessionRepo.updateCurrentDir(firstSessionId, "/deep/dir")

        FilesStartupHandler.applyInitialOpen(
            state = InitialOpenState(networkConfigId = configId, startPath = null, safTreeUri = null),
            requestedNetworkEntry = false,
            networkConfigRepository = networkRepo,
            folderSessionRepository = sessionRepo,
            sessionSourceStore = sourceStore,
            currentSessionStore = sessionStore,
            onInvalidStartPath = {}
        )

        assertEquals("再次进入同一网络配置应复用会话", 1, sessionRepo.getAll().size)
        assertEquals(firstSessionId, sessionStore.get())
        assertEquals("/deep/dir", sessionRepo.getById(firstSessionId)!!.currentPath)
    }

    @Test
    fun applyInitialOpen_noCredentialIssue_createsNetworkSession() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configId = db.networkConfigDao().insert(
            NetworkConfigEntity(
                name = "plain",
                protocol = NetworkProtocol.FTP,
                host = "host",
                port = 21,
                username = "user",
                password = "plain-password", // 旧明文，decrypt 原样返回
                defaultPath = "/docs"
            )
        )

        var credentialUnavailable = false
        FilesStartupHandler.applyInitialOpen(
            state = InitialOpenState(networkConfigId = configId, startPath = null, safTreeUri = null),
            requestedNetworkEntry = false,
            networkConfigRepository = networkRepo,
            folderSessionRepository = sessionRepo,
            sessionSourceStore = AppSessionSourceStore(context),
            currentSessionStore = AppCurrentSessionStore(),
            onInvalidStartPath = {},
            onCredentialUnavailable = { credentialUnavailable = true }
        )

        assertEquals(false, credentialUnavailable)
        assertEquals(1, sessionRepo.getAll().size)
    }
}
