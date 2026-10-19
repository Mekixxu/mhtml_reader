package com.html_reader

import core.common.AppError
import core.common.DefaultDispatcherProvider
import core.fileops.model.FileOpState
import core.fileops.usecase.DeleteRecursivelyUseCase
import core.vfs.IFileSystem
import core.vfs.model.VfsEntry
import core.vfs.model.VfsPath
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteRecursivelyUseCaseTest {

    @Test
    fun delete_moreThanOnePage_deletesEveryEntryAndReportsSuccess() = runBlocking {
        val root = VfsPath.LocalFile("/root")
        val files = (1..150).map { index ->
            VfsEntry(
                name = "file$index",
                path = VfsPath.LocalFile("/root/file$index"),
                isDirectory = false,
                sizeBytes = 1L,
                lastModifiedEpochMs = 0L
            )
        }
        val fileSystem = FakeFileSystem(mapOf(root to files))

        val states = mutableListOf<FileOpState>()
        DeleteRecursivelyUseCase(fileSystem, DefaultDispatcherProvider()).delete(root) { states += it }

        assertEquals(files.size + 1, fileSystem.deleted.size)
        assertTrue(root in fileSystem.deleted)
        files.forEach { assertTrue(it.path in fileSystem.deleted) }
        assertTrue(states.single() is FileOpState.Success)
    }

    @Test
    fun delete_failure_isPropagatedAndSuccessIsNotReported() = runBlocking {
        val root = VfsPath.LocalFile("/root")
        val first = VfsEntry(
            name = "first",
            path = VfsPath.LocalFile("/root/first"),
            isDirectory = false,
            sizeBytes = 1L,
            lastModifiedEpochMs = 0L
        )
        val second = first.copy(name = "second", path = VfsPath.LocalFile("/root/second"))
        val fileSystem = FakeFileSystem(
            entriesByDir = mapOf(root to listOf(first, second)),
            failingDeletes = setOf(first.path)
        )

        val states = mutableListOf<FileOpState>()
        val result = runCatching {
            DeleteRecursivelyUseCase(fileSystem, DefaultDispatcherProvider()).delete(root) { states += it }
        }

        assertTrue(result.isFailure)
        assertTrue(states.none { it is FileOpState.Success })
    }


    @Test
    fun delete_nestedDirectory_deletesChildrenBeforeDirectory() = runBlocking {
        val root = VfsPath.LocalFile("/root")
        val child = VfsPath.LocalFile("/root/child")
        val nestedFile = VfsEntry(
            name = "nested",
            path = VfsPath.LocalFile("/root/child/nested"),
            isDirectory = false,
            sizeBytes = 1L,
            lastModifiedEpochMs = 0L
        )
        val childEntry = VfsEntry(
            name = "child",
            path = child,
            isDirectory = true,
            sizeBytes = 0L,
            lastModifiedEpochMs = 0L
        )
        val fileSystem = FakeFileSystem(
            mapOf(
                root to listOf(childEntry),
                child to listOf(nestedFile)
            )
        )

        DeleteRecursivelyUseCase(fileSystem, DefaultDispatcherProvider()).delete(root) {}
        val nestedIndex = fileSystem.deleted.indexOf(nestedFile.path)
        val childIndex = fileSystem.deleted.indexOf(child)
        val rootIndex = fileSystem.deleted.indexOf(root)

        assertTrue(nestedIndex in 0 until childIndex)
        assertTrue(childIndex in 0 until rootIndex)
    }

    private class FakeFileSystem(
        private val entriesByDir: Map<VfsPath, List<VfsEntry>>,
        private val failingDeletes: Set<VfsPath> = emptySet()
    ) : IFileSystem {
        val deleted = mutableListOf<VfsPath>()

        override suspend fun list(dir: VfsPath, offset: Int, limit: Int): Result<List<VfsEntry>> {
            val entries = entriesByDir[dir] ?: return Result.failure(AppError.NotFound)
            return Result.success(entries.drop(offset).take(limit))
        }

        override suspend fun delete(path: VfsPath): Result<Unit> {
            if (path in failingDeletes) return Result.failure(AppError.IoError("delete failed"))
            deleted += path
            return Result.success(Unit)
        }

        override suspend fun openInputStream(path: VfsPath): Result<InputStream> =
            Result.failure(AppError.UnsupportedOperation)

        override suspend fun openOutputStream(path: VfsPath, append: Boolean): Result<OutputStream> =
            Result.failure(AppError.UnsupportedOperation)

        override suspend fun createFile(parentDir: VfsPath, name: String, mimeType: String?): Result<VfsPath> =
            Result.failure(AppError.UnsupportedOperation)

        override suspend fun exists(path: VfsPath): Result<Boolean> = Result.success(path in entriesByDir)

        override suspend fun createFolder(path: VfsPath): Result<Unit> =
            Result.failure(AppError.UnsupportedOperation)

        override suspend fun rename(from: VfsPath, toName: String): Result<VfsPath> =
            Result.failure(AppError.UnsupportedOperation)

        override suspend fun move(from: VfsPath, toDir: VfsPath): Result<VfsPath> =
            Result.failure(AppError.UnsupportedOperation)

        override suspend fun copy(from: VfsPath, toDir: VfsPath): Result<VfsPath> =
            Result.failure(AppError.UnsupportedOperation)

        override suspend fun lastModified(path: VfsPath): Result<Long> = Result.success(0L)
    }
}
