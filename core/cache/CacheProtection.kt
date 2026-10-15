package core.cache

import java.io.File

/**
 * 缓存保护的唯一来源。
 *
 * 淘汰与维护清理必须经由 [withProtectionLock]（或 [deleteCacheDirIfUnprotected]）在**同一把锁**内
 * 完成「读取保护集 + 删除」，否则会出现「先取 activeKeys 快照 → 随后建立新保护 → 仍按过期快照删除」的竞态。
 *
 * 运行时由 [TabCacheRegistry] 实现（租约引用计数即保护状态）。
 */
interface CacheProtection {
    /** 当前受保护的 cacheKey 快照（用于淘汰/清理的候选过滤）。 */
    fun protectedKeys(): Set<String>

    /**
     * 在保护锁内执行 [block]，用于「快照 + 删除」的临界区。
     * 期间不会建立新的保护（新 `acquire` 会等待该锁）。
     */
    fun <T> withProtectionLock(block: () -> T): T

    /**
     * 在保护锁内判定并删除 keyDir；仅当该 cacheKey 无任何保护时执行删除。
     * @return true 表示确实删除了目录（真正释放了空间）。
     */
    fun deleteCacheDirIfUnprotected(cacheKey: String, dir: File): Boolean

    companion object {
        /** 无保护来源（测试或独立使用淘汰器时）。 */
        val NONE: CacheProtection = object : CacheProtection {
            override fun protectedKeys(): Set<String> = emptySet()
            override fun <T> withProtectionLock(block: () -> T): T = block()
            override fun deleteCacheDirIfUnprotected(cacheKey: String, dir: File): Boolean =
                dir.deleteRecursively()
        }
    }
}
