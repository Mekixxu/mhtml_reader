package com.html_reader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import core.common.AppError
import core.common.DefaultDispatcherProvider
import core.fileops.model.FileOpRequest
import core.fileops.model.FileOpState
import core.fileops.usecase.ExecuteFileOpUseCase
import core.fileops.util.NameConflictResolver
import core.vfs.local.LocalFileSystem
import core.vfs.model.VfsPath
import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalFileSystemRenameTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dispatchers = DefaultDispatcherProvider()
    private val fileSystem = LocalFileSystem(context, dispatchers)

    @Test
    fun rename_toExistingTarget_failsAndPreservesBothFiles() = runBlocking {
        val source = File(tempFolder.root, "a.txt").apply { writeText("A") }
        val target = File(tempFolder.root, "b.txt").apply { writeText("B") }

        val result = fileSystem.rename(VfsPath.LocalFile(source.absolutePath), "b.txt")

        assertTrue(result.isFailure)
        assertEquals(AppError.Conflict, result.exceptionOrNull())
        assertEquals("A", source.readText())
        assertEquals("B", target.readText())
    }

    @Test
    fun rename_sameName_isIdempotentAndKeepsContent() = runBlocking {
        val source = File(tempFolder.root, "same.txt").apply { writeText("same") }

        val result = fileSystem.rename(VfsPath.LocalFile(source.absolutePath), "same.txt")

        assertTrue(result.isSuccess)
        assertEquals("same", source.readText())
    }

    @Test
    fun rename_rejectsTraversalEmptyAndSeparators() = runBlocking {
        val source = File(tempFolder.root, "safe.txt").apply { writeText("safe") }
        val outside = File(tempFolder.root.parentFile, "outside.txt")

        val traversal = fileSystem.rename(VfsPath.LocalFile(source.absolutePath), "../outside.txt")
        val absolute = fileSystem.rename(VfsPath.LocalFile(source.absolutePath), outside.absolutePath)
        val empty = fileSystem.rename(VfsPath.LocalFile(source.absolutePath), "")
        val slash = fileSystem.rename(VfsPath.LocalFile(source.absolutePath), "sub/target.txt")

        assertTrue(traversal.isFailure)
        assertTrue(absolute.isFailure)
        assertTrue(empty.isFailure)
        assertTrue(slash.isFailure)
        assertEquals(AppError.InvalidName, traversal.exceptionOrNull())
        assertFalse(outside.exists())
        assertTrue(source.exists())
        assertEquals("safe", source.readText())
    }

    @Test
    fun rename_acceptsChineseAndMultiDotExtensionName() = runBlocking {
        val source = File(tempFolder.root, "文档.v1.mhtml").apply { writeText("v1") }

        val result = fileSystem.rename(VfsPath.LocalFile(source.absolutePath), "最终版.v2.mhtml")

        assertTrue(result.isSuccess)
        val renamed = File(tempFolder.root, "最终版.v2.mhtml")
        assertTrue(renamed.exists())
        assertEquals("v1", renamed.readText())
    }

    @Test
    fun createFolder_rejectsTraversalBeforeFileSystemWrite() = runBlocking {
        val parent = tempFolder.newFolder("parent")
        val outside = File(tempFolder.root.parentFile, "outside")

        val useCase = ExecuteFileOpUseCase(
            fileSystem = fileSystem,
            nameResolver = NameConflictResolver(fileSystem, dispatchers),
            dispatcherProvider = dispatchers
        )
        val states = useCase.execute(
            FileOpRequest.CreateFolder(
                parentDir = VfsPath.LocalFile(parent.absolutePath),
                name = "../outside"
            )
        ).toList()

        val error = states.filterIsInstance<FileOpState.Error>().single()
        assertEquals(AppError.InvalidName, error.error)
        assertFalse(outside.exists())
    }
}
