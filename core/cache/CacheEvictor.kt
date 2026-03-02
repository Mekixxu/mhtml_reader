package core.cache

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * LRU淘汰器，目录分区后按最后修改时间淘汰
 */
class CacheEvictor(
    private val cacheRoot: File,
    private val maxBytes: Long = 2L * 1024 * 1024 * 1024
) {
    suspend fun evictIfNeeded() = withContext(Dispatchers.IO) {
        var total: Long = cacheRoot.listFiles()?.sumOf { it.sizeAndChildren() } ?: 0L
        if (total <= maxBytes) return@withContext

        val items = cacheRoot.listFiles()
            ?.filter { it.isDirectory }
            ?.flatMap { dir -> dir.listFiles()?.filter { it.isDirectory }?.map { it } ?: emptyList() }
            ?.map { it to (it.lastModified()) }
            ?.sortedBy { it.second } ?: return@withContext

        for ((dir, _) in items) {
            val sz = dir.sizeAndChildren()
            dir.deleteRecursively()
            total -= sz
            if (total <= maxBytes) break
        }
    }
}

/**
 * 仅限本地/缓存目录类型
 */
private fun File.sizeAndChildren(): Long {
    if (!exists()) return 0L
    if (isFile) return length()
    return walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
