package com.html_reader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import core.common.DefaultDispatcherProvider
import core.fileops.model.ConflictStrategy
import core.fileops.model.FileOpState
import core.fileops.usecase.MoveUseCase
import core.fileops.util.NameConflictResolver
import core.vfs.local.LocalFileSystem
import core.vfs.model.VfsPath
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MoveUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var useCase: MoveUseCase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dispatchers = DefaultDispatcherProvider()
        val fileSystem = LocalFileSystem(context, dispatchers)
        useCase = MoveUseCase(fileSystem, NameConflictResolver(fileSystem, dispatchers), dispatchers)
    }

    @Test
    fun move_autoRename_doesNotOverwriteExistingTarget() = runBlocking {
        val srcDir = tempFolder.newFolder("src")
        val dstDir = tempFolder.newFolder("dst")
        val source = File(srcDir, "file.mhtml").apply { writeText("source-content") }
        val existing = File(dstDir, "file.mhtml").apply { writeText("existing-content") }

        val states = mutableListOf<FileOpState>()
        useCase.move(
            from = VfsPath.LocalFile(source.absolutePath),
            toDir = VfsPath.LocalFile(dstDir.absolutePath),
            strategy = ConflictStrategy.AUTO_RENAME
        ) { state -> states += state }

        assertTrue(states.any { it is FileOpState.Success })
        assertEquals("existing-content", existing.readText())
        assertFalse(source.exists())
        val renamed = File(dstDir, "file(1).mhtml")
        assertTrue(renamed.exists())
        assertEquals("source-content", renamed.readText())
    }

    @Test
    fun move_noConflict_movesToOriginalName() = runBlocking {
        val srcDir = tempFolder.newFolder("src2")
        val dstDir = tempFolder.newFolder("dst2")
        val source = File(srcDir, "file.mhtml").apply { writeText("content") }

        val states = mutableListOf<FileOpState>()
        useCase.move(
            from = VfsPath.LocalFile(source.absolutePath),
            toDir = VfsPath.LocalFile(dstDir.absolutePath),
            strategy = ConflictStrategy.AUTO_RENAME
        ) { state -> states += state }

        assertTrue(states.any { it is FileOpState.Success })
        assertEquals("content", File(dstDir, "file.mhtml").readText())
        assertFalse(source.exists())
    }
}
