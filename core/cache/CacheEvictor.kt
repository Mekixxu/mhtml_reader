package core.cache

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 淘汰结果，便于调用方区分「文件本身超上限」与「活跃缓存占用导致无法腾挪」。
 */
enum class EvictionResult {
    READY,
    EXCEEDS_LIMIT,
    CANNOT_FREE
}

/**
 * LRU淘汰器，目录分区后按最后修改时间淘汰。
 * 同时统计根/一级目录下的散落文件，避免缓存总量被低估。
 */
class CacheEvictor(
    private val cacheRoot: File,
    private val maxBytes: Long = 2L * 1024 * 1024 * 1024
) {
    private data class CacheItem(
        val file: File,
        val size: Long,
        val protectedName: String? // keyDir 名称，散落文件为 null（不可受保护）
    )

    suspend fun evictOldFiles(maxAgeMs: Long, protectedKeys: Set<String> = emptySet()) =
        withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - maxAgeMs
            collectCacheItems()
                .filter { it.isDeletable(protectedKeys) }
                .forEach { item ->
                    if (item.file.lastModified() < cutoff) {
                        item.file.deleteRecursively()
                    }
                }
        }

    /**
     * 为 requiredBytes 腾空间。
     * - protectedKeys / currentKey 对应的缓存目录永不淘汰（活跃 tab、在途拷贝、本次待写入缓存）。
     * - requiredBytes 超过上限时直接失败，绝不为了腾空间清空缓存。
     * @return READY 表示空间已就绪；EXCEEDS_LIMIT 表示文件本身超上限；CANNOT_FREE 表示受保护缓存占用导致无法腾挪。
     */
    suspend fun makeRoomFor(
        requiredBytes: Long,
        protectedKeys: Set<String> = emptySet(),
        currentKey: String? = null
    ): EvictionResult = withContext(Dispatchers.IO) {
        if (requiredBytes >= maxBytes) return@withContext EvictionResult.EXCEEDS_LIMIT
        val protected = protectedKeys + setOfNotNull(currentKey)
        val all = collectCacheItems()
        var total = all.sumOf { it.size }
        val limit = maxBytes - requiredBytes
        if (total <= limit) return@withContext EvictionResult.READY

        for (item in all.filter { it.isDeletable(protected) }.sortedBy { it.file.lastModified() }) {
            item.file.deleteRecursively()
            total -= item.size
            if (total <= limit) return@withContext EvictionResult.READY
        }
        if (total <= limit) EvictionResult.READY else EvictionResult.CANNOT_FREE
    }

    suspend fun evictIfNeeded(protectedKeys: Set<String> = emptySet()) =
        makeRoomFor(0L, protectedKeys)

    private fun CacheItem.isDeletable(protectedKeys: Set<String>): Boolean =
        protectedName == null || protectedName !in protectedKeys

    private fun collectCacheItems(): List<CacheItem> {
        val items = mutableListOf<CacheItem>()
        cacheRoot.listFiles()?.forEach { entry ->
            if (entry.isDirectory) {
                entry.listFiles()?.forEach { child ->
                    if (child.isDirectory) {
                        items += CacheItem(child, child.sizeAndChildren(), child.name)
                    } else {
                        items += CacheItem(child, child.length(), null)
                    }
                }
            } else {
                items += CacheItem(entry, entry.length(), null)
            }
        }
        return items
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
