package com.html_reader

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.html_reader.files.FilesSessionPlanner
import core.common.DefaultDispatcherProvider
import core.database.AppDatabase
import core.session.repo.FolderSessionRepository
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderSessionPositionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var repo: FolderSessionRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = FolderSessionRepository(db.folderSessionDao(), DefaultDispatcherProvider())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun updateCurrentDir_thenRead_keepsNestedDirectory() = runBlocking {
        val root = tempFolder.newFolder("root")
        val nested = File(root, "a/b").apply { mkdirs() }
        val id = repo.add("session", root.absolutePath)

        repo.updateCurrentDir(id, nested.absolutePath)

        val session = repo.getById(id)!!
        assertEquals(nested.absolutePath, session.currentPath)

        val plan = FilesSessionPlanner.build(
            session = session,
            linkedNetworkConfig = null,
            defaultRootDir = root,
            configuredFtpCharsetName = { null }
        )
        assertEquals(nested.absolutePath, plan.localDir?.absolutePath)
    }

    @Test
    fun updateCurrentDir_thenSortOption_doesNotClobberPath() = runBlocking {
        val root = tempFolder.newFolder("root2")
        val nested = File(root, "deep").apply { mkdirs() }
        val id = repo.add("session2", root.absolutePath)

        repo.updateCurrentDir(id, nested.absolutePath)
        repo.updateSortOption(id, 4)

        val session = repo.getById(id)!!
        assertEquals(nested.absolutePath, session.currentPath)
        assertEquals(4, session.sortOption)
    }
}
