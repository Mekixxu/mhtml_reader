package com.html_reader

import core.cache.CacheEvictor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CacheEvictorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun makeRoomFor_deletesOldestCacheDirectoryFirst() = runBlocking {
        val root = tempFolder.newFolder("app_cache")
        val typeDir = File(root, "mhtml").apply { mkdirs() }
        val now = System.currentTimeMillis()
        val old = createCacheDir(typeDir, "old", now - 3 * 86_400_000L, bytes = 100)
        val middle = createCacheDir(typeDir, "middle", now - 2 * 86_400_000L, bytes = 100)
        val newest = createCacheDir(typeDir, "newest", now - 86_400_000L, bytes = 100)

        val evictor = CacheEvictor(cacheRoot = root, maxBytes = 250)
        evictor.makeRoomFor(requiredBytes = 50)

        assertFalse(old.exists())
        assertTrue(middle.exists())
        assertTrue(newest.exists())
    }

    private fun createCacheDir(typeDir: File, name: String, lastModified: Long, bytes: Int): File {
        val dir = File(typeDir, name).apply { mkdirs() }
        File(dir, "content.mhtml").apply {
            writeBytes(ByteArray(bytes))
            setLastModified(lastModified)
        }
        dir.setLastModified(lastModified)
        return dir
    }
}
