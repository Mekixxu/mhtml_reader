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
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FilesStartupHandlerTest {

    private lateinit var db: AppDatabase
    private lateinit var networkRepo: NetworkConfigRepository
    private lateinit var sessionRepo: FolderSessionRepository

    @get:Rule
    val tempFolder = TemporaryFolder()

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

    @Test
    fun applyInitialOpen_localEntryWhileNetworkSessionActive_doesNotHijackNetworkSession() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configId = db.networkConfigDao().insert(
            NetworkConfigEntity(
                name = "ftp",
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
        // 先建立网络会话并浏览到深层远端路径
        FilesStartupHandler.applyInitialOpen(
            state = InitialOpenState(networkConfigId = configId, startPath = null, safTreeUri = null),
            requestedNetworkEntry = false,
            networkConfigRepository = networkRepo,
            folderSessionRepository = sessionRepo,
            sessionSourceStore = sourceStore,
            currentSessionStore = sessionStore,
            onInvalidStartPath = {}
        )
        val networkSessionId = sessionStore.get()!!
        sessionRepo.updateCurrentDir(networkSessionId, "/deep/remote")

        // 打开本地目录（本地入口），此前无任何本地会话
        val localDir = tempFolder.newFolder("docs")
        var invalidPath = false
        FilesStartupHandler.applyInitialOpen(
            state = InitialOpenState(networkConfigId = null, startPath = localDir.absolutePath, safTreeUri = null),
            requestedNetworkEntry = false,
            networkConfigRepository = networkRepo,
            folderSessionRepository = sessionRepo,
            sessionSourceStore = sourceStore,
            currentSessionStore = sessionStore,
            onInvalidStartPath = { invalidPath = true }
        )

        // 网络会话不得被解绑、远端路径不得被改写（混合会话缺陷）
        assertEquals("/deep/remote", sessionRepo.getById(networkSessionId)!!.currentPath)
        assertEquals(configId, sourceStore.getNetworkConfigId(networkSessionId))
        assertEquals(false, invalidPath)
        // 当前会话切到承载本地目录的新建本地会话
        val localSessionId = sessionStore.get()!!
        assertTrue("当前会话应切到本地会话", localSessionId != networkSessionId)
        assertEquals(localDir.absolutePath, sessionRepo.getById(localSessionId)!!.currentPath)
    }

    @Test
    fun applyInitialOpen_localEntryWhileNetworkSessionActive_reusesLocalSessionWithPosition() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configId = db.networkConfigDao().insert(
            NetworkConfigEntity(
                name = "ftp",
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
        val networkSessionId = sessionStore.get()!!
        sessionRepo.updateCurrentDir(networkSessionId, "/deep/remote")

        // 既有本地会话，当前位置位于请求目录内（应复用并保留其位置）
        val localRoot = tempFolder.newFolder("root")
        val nested = File(localRoot, "nested").apply { mkdirs() }
        val localSessionId = sessionRepo.add("Default", localRoot.absolutePath)
        sessionRepo.updateCurrentDir(localSessionId, nested.absolutePath)

        FilesStartupHandler.applyInitialOpen(
            state = InitialOpenState(networkConfigId = null, startPath = localRoot.absolutePath, safTreeUri = null),
            requestedNetworkEntry = false,
            networkConfigRepository = networkRepo,
            folderSessionRepository = sessionRepo,
            sessionSourceStore = sourceStore,
            currentSessionStore = sessionStore,
            onInvalidStartPath = {}
        )

        assertEquals("应复用既有本地会话", localSessionId, sessionStore.get())
        assertEquals("应保留本地会话的深层位置", nested.absolutePath, sessionRepo.getById(localSessionId)!!.currentPath)
        assertEquals("网络会话不受影响", "/deep/remote", sessionRepo.getById(networkSessionId)!!.currentPath)
    }

    @Test
    fun applyInitialOpen_invalidLocalPathWhileNetworkSessionActive_keepsNetworkSession() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configId = db.networkConfigDao().insert(
            NetworkConfigEntity(
                name = "ftp",
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
        val networkSessionId = sessionStore.get()!!
        sessionRepo.updateCurrentDir(networkSessionId, "/deep/remote")

        // 打开不存在的本地路径：不得把无效路径写进网络会话的远端路径
        val missingPath = File(tempFolder.root, "missing").absolutePath
        var invalidPath = false
        FilesStartupHandler.applyInitialOpen(
            state = InitialOpenState(networkConfigId = null, startPath = missingPath, safTreeUri = null),
            requestedNetworkEntry = false,
            networkConfigRepository = networkRepo,
            folderSessionRepository = sessionRepo,
            sessionSourceStore = sourceStore,
            currentSessionStore = sessionStore,
            onInvalidStartPath = { invalidPath = true }
        )

        assertTrue("应提示路径不可用", invalidPath)
        assertEquals("/deep/remote", sessionRepo.getById(networkSessionId)!!.currentPath)
        assertEquals(configId, sourceStore.getNetworkConfigId(networkSessionId))
    }
}
