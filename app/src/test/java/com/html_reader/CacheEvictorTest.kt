package com.html_reader

import core.cache.CacheEvictor
import core.cache.EvictionResult
import org.junit.Assert.assertEquals
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

    @Test
    fun makeRoomFor_requiredExceedsMax_reportsExceedsLimit() = runBlocking {
        val root = tempFolder.newFolder("app_cache_oversize")
        val typeDir = File(root, "mhtml").apply { mkdirs() }
        val cached = createCacheDir(typeDir, "cached", System.currentTimeMillis() - 86_400_000L, bytes = 100)

        val evictor = CacheEvictor(cacheRoot = root, maxBytes = 200)
        val result = evictor.makeRoomFor(requiredBytes = 300)

        assertEquals(EvictionResult.EXCEEDS_LIMIT, result)
        assertTrue(cached.exists())
    }

    @Test
    fun makeRoomFor_skipsProtectedAndCurrentKeys() = runBlocking {
        val root = tempFolder.newFolder("app_cache_protected")
        val typeDir = File(root, "mhtml").apply { mkdirs() }
        val now = System.currentTimeMillis()
        val active = createCacheDir(typeDir, "active", now - 3 * 86_400_000L, bytes = 100)
        val current = createCacheDir(typeDir, "current", now - 2 * 86_400_000L, bytes = 100)
        val idle = createCacheDir(typeDir, "idle", now - 86_400_000L, bytes = 100)

        val evictor = CacheEvictor(cacheRoot = root, maxBytes = 400)
        val result = evictor.makeRoomFor(
            requiredBytes = 150,
            protectedKeys = setOf("active"),
            currentKey = "current"
        )

        assertEquals(EvictionResult.READY, result)
        assertTrue(active.exists())
        assertTrue(current.exists())
        assertFalse(idle.exists())
    }

    @Test
    fun makeRoomFor_protectedUsagePreventsFitting_reportsCannotFree() = runBlocking {
        val root = tempFolder.newFolder("app_cache_protected_full")
        val typeDir = File(root, "mhtml").apply { mkdirs() }
        val now = System.currentTimeMillis()
        val active = createCacheDir(typeDir, "active", now - 3 * 86_400_000L, bytes = 100)
        val current = createCacheDir(typeDir, "current", now - 2 * 86_400_000L, bytes = 100)
        val idle = createCacheDir(typeDir, "idle", now - 86_400_000L, bytes = 100)

        val evictor = CacheEvictor(cacheRoot = root, maxBytes = 250)
        val result = evictor.makeRoomFor(
            requiredBytes = 100,
            protectedKeys = setOf("active"),
            currentKey = "current"
        )

        assertEquals(EvictionResult.CANNOT_FREE, result)
        assertTrue(active.exists())
        assertTrue(current.exists())
        assertFalse(idle.exists())
    }

    @Test
    fun makeRoomFor_countsAndEvictsLooseFiles() = runBlocking {
        val root = tempFolder.newFolder("app_cache_loose")
        val typeDir = File(root, "mhtml").apply { mkdirs() }
        val now = System.currentTimeMillis()
        val cached = createCacheDir(typeDir, "cached", now - 3 * 86_400_000L, bytes = 100)
        val typeLoose = File(typeDir, "loose.tmp").apply {
            writeBytes(ByteArray(100)); setLastModified(now - 4 * 86_400_000L)
        }
        val rootLoose = File(root, "root.tmp").apply {
            writeBytes(ByteArray(100)); setLastModified(now - 5 * 86_400_000L)
        }

        val evictor = CacheEvictor(cacheRoot = root, maxBytes = 250)
        val result = evictor.makeRoomFor(requiredBytes = 100)

        assertEquals(EvictionResult.READY, result)
        assertFalse(rootLoose.exists())
        assertFalse(typeLoose.exists())
        assertTrue(cached.exists())
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
