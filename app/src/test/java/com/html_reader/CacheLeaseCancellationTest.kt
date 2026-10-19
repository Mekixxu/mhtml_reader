package com.html_reader

import core.cache.TabCacheRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CacheLeaseCancellationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun cancelledRelease_stillDropsProtectionReference() = runBlocking {
        val registry = TabCacheRegistry(tempFolder.newFolder("cache"))
        val lease = registry.acquire(cacheKey = "cache-key", contentType = "mhtml")

        launch(Dispatchers.Default) {
            cancel()
            lease.release()
        }.join()

        assertTrue(registry.activeCacheKeys().isEmpty())
    }
}
