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
    suspend fun evictOldFiles(maxAgeMs: Long, protectedKeys: Set<String> = emptySet()) =
        withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - maxAgeMs
            collectCacheDirs()
                .filter { it.name !in protectedKeys }
                .forEach { dir ->
                    if (dir.lastModified() < cutoff) {
                        dir.deleteRecursively()
                    }
                }
        }

    /**
     * 为 requiredBytes 腾空间。
     * - protectedKeys / currentKey 对应的缓存目录永不淘汰（活跃 tab、本次待写入缓存）。
     * - requiredBytes 超过上限时直接失败，绝不为了腾空间清空缓存。
     * @return true 表示空间已就绪；false 表示无法满足。
     */
    suspend fun makeRoomFor(
        requiredBytes: Long,
        protectedKeys: Set<String> = emptySet(),
        currentKey: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (requiredBytes >= maxBytes) return@withContext false
        val protected = protectedKeys + setOfNotNull(currentKey)
        val all = collectCacheDirs().map { dir -> dir to dir.sizeAndChildren() }
        var total = all.sumOf { it.second }
        val limit = maxBytes - requiredBytes
        if (total <= limit) return@withContext true

        val deletable = all.filter { it.first.name !in protected }
        for ((dir, size) in deletable.sortedBy { it.first.lastModified() }) {
            dir.deleteRecursively()
            total -= size
            if (total <= limit) return@withContext true
        }
        total <= limit
    }

    suspend fun evictIfNeeded(protectedKeys: Set<String> = emptySet()) =
        makeRoomFor(0L, protectedKeys)

    private fun collectCacheDirs(): List<File> =
        cacheRoot.listFiles()
            ?.filter { it.isDirectory }
            ?.flatMap { dir -> dir.listFiles()?.filter { it.isDirectory } ?: emptyList() }
            ?: emptyList()
}

/**
 * 仅限本地/缓存目录类型
 */
private fun File.sizeAndChildren(): Long {
    if (!exists()) return 0L
    if (isFile) return length()
    return walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
