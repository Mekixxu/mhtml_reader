package core.cache

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 淘汰结果，便于调用方区分「文件本身超上限」与「活跃/在途占用导致无法腾挪」。
 */
enum class EvictionResult {
    READY,
    EXCEEDS_LIMIT,
    CANNOT_FREE
}

/**
 * LRU 淘汰器 + 容量预算（reservation）。
 *
 * - 目录分区后按最后修改时间淘汰，同时统计根/一级目录下的散落文件，避免总量被低估。
 * - [reserve] / [growReservation] / [releaseReservation] 维护「受管理已提交字节 + 在途预约 ≤ maxBytes」不变量：
 *   「检查可用量 → 选择淘汰项 → 核对实际删除 → 登记预约」在 [budgetLock] 内一致完成，不同 cacheKey 共用同一预算。
 * - 在途预约按预约值而非已落盘值计入，故写入中的 `.part` 不会与预约重复计数。
 * - [protection] 提供受保护集合与「保护锁内删除」，避免按过期快照误删新保护（Q2/Q3 维护竞态）。
 */
class CacheEvictor(
    private val cacheRoot: File,
    private val maxBytes: Long = 2L * 1024 * 1024 * 1024,
    private val protection: CacheProtection = CacheProtection.NONE
) {
    private data class CacheItem(
        val file: File,
        val size: Long,
        val key: String? // keyDir 名称，散落文件为 null（不可受保护）
    )

    private val budgetLock = Mutex()

    /** cacheKey -> 预约字节；仅在 [budgetLock] 内写入。 */
    private val reservations = ConcurrentHashMap<String, Long>()

    suspend fun evictOldFiles(maxAgeMs: Long, protectedKeys: Set<String> = emptySet()) =
        withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - maxAgeMs
            protection.withProtectionLock {
                val protected = protectedKeys + protection.protectedKeys() + reservations.keys
                collectCacheItems()
                    .filter { it.isDeletable(protected) }
                    .forEach { item ->
                        if (item.file.lastModified() < cutoff) {
                            deleteItem(item)
                        }
                    }
            }
        }

    /**
     * 为 requiredBytes 腾空间（不登记持久预约，供既有调用方/维护使用）。
     * - protectedKeys / currentKey / 保护来源 / 在途预约对应的缓存目录永不淘汰。
     * - requiredBytes 超过上限时直接失败，绝不为了腾空间清空缓存。
     * @return READY 表示空间已就绪；EXCEEDS_LIMIT 表示文件本身超上限；CANNOT_FREE 表示受保护占用导致无法腾挪。
     */
    suspend fun makeRoomFor(
        requiredBytes: Long,
        protectedKeys: Set<String> = emptySet(),
        currentKey: String? = null
    ): EvictionResult = withContext(Dispatchers.IO) {
        budgetLock.withLock { ensureRoom(requiredBytes, protectedKeys, currentKey) }
    }

    /**
     * 为 cacheKey 登记/更新预约到 expectedBytes（已知长度在首块前一次性预约）。
     */
    suspend fun reserve(
        cacheKey: String,
        expectedBytes: Long,
        protectedKeys: Set<String> = emptySet()
    ): EvictionResult = withContext(Dispatchers.IO) {
        budgetLock.withLock { applyReservation(cacheKey, expectedBytes, protectedKeys) }
    }

    /**
     * 未知长度按块扩容预约：首次精确到 neededBytes，之后几何增长以减少扫描。
     */
    suspend fun growReservation(
        cacheKey: String,
        neededBytes: Long,
        protectedKeys: Set<String> = emptySet()
    ): EvictionResult = withContext(Dispatchers.IO) {
        budgetLock.withLock {
            val current = reservations[cacheKey] ?: 0L
            if (neededBytes <= current) return@withLock EvictionResult.READY
            val target = if (current <= 0L) {
                neededBytes
            } else {
                maxOf(neededBytes, minOf(current * 2, maxBytes - 1))
            }
            applyReservation(cacheKey, target, protectedKeys)
        }
    }

    /** 提交/失败/取消后归还预约（对不存在的键为空操作）。 */
    suspend fun releaseReservation(cacheKey: String) {
        budgetLock.withLock { reservations.remove(cacheKey) }
    }

    suspend fun evictIfNeeded(protectedKeys: Set<String> = emptySet()) =
        makeRoomFor(0L, protectedKeys)

    private fun applyReservation(
        cacheKey: String,
        target: Long,
        protectedKeys: Set<String>
    ): EvictionResult {
        if (target < 0L || target >= maxBytes) return EvictionResult.EXCEEDS_LIMIT
        val current = reservations[cacheKey] ?: 0L
        if (target <= current) {
            reservations[cacheKey] = target
            return EvictionResult.READY
        }
        val result = ensureRoom(target - current, protectedKeys, currentKey = cacheKey)
        if (result == EvictionResult.READY) {
            reservations[cacheKey] = target
        }
        return result
    }

    private fun ensureRoom(
        requiredBytes: Long,
        protectedKeys: Set<String>,
        currentKey: String?
    ): EvictionResult {
        if (requiredBytes >= maxBytes) return EvictionResult.EXCEEDS_LIMIT
        return protection.withProtectionLock {
            val protected = protectedKeys + setOfNotNull(currentKey) +
                protection.protectedKeys() + reservations.keys
            val limit = maxBytes - requiredBytes
            var total = managedTotal()
            if (total <= limit) return@withProtectionLock EvictionResult.READY

            val candidates = collectCacheItems()
                .filter { it.isDeletable(protected) }
                .sortedBy { it.file.lastModified() }
            for (item in candidates) {
                // 删除失败（含 Windows 句柄占用）不算释放空间，继续尝试其它候选
                if (deleteItem(item)) total -= item.size
                if (total <= limit) return@withProtectionLock EvictionResult.READY
            }
            if (total <= limit) EvictionResult.READY else EvictionResult.CANNOT_FREE
        }
    }

    /**
     * 受管理总量：在途预约的 cacheKey 按「预约值与盘上大小取大」计入，其余按盘上大小计入；
     * 尚无目录的预约单独累加。避免在途文件与预约双计，也避免漏计。
     */
    private fun managedTotal(): Long {
        val items = collectCacheItems()
        var total = 0L
        val reservedSeen = HashSet<String>()
        for (item in items) {
            val key = item.key
            val reserved = key?.let { reservations[it] }
            if (key != null && reserved != null) {
                total += maxOf(reserved, item.size)
                reservedSeen += key
            } else {
                total += item.size
            }
        }
        reservations.forEach { (key, bytes) ->
            if (key !in reservedSeen) total += bytes
        }
        return total
    }

    private fun deleteItem(item: CacheItem): Boolean {
        val key = item.key
        return if (key == null) {
            item.file.deleteRecursively()
        } else {
            protection.deleteCacheDirIfUnprotected(key, item.file)
        }
    }

    private fun CacheItem.isDeletable(protectedKeys: Set<String>): Boolean =
        key == null || key !in protectedKeys

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
