package core.cache

import java.io.File
import java.util.HashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

private const val LEASE_STATE_HELD = 0
private const val LEASE_STATE_TRANSFERRED = 1
private const val LEASE_STATE_RELEASED = 2

/**
 * 同一 cacheRoot 下的缓存保护与标签归属唯一来源。
 *
 * - 保护以「cacheKey 引用计数」表达：拷贝开始/缓存命中时 `acquire`，标签接管时移交，标签关闭或
 *   放弃路径释放；只有计数归零才允许删除缓存目录。
 * - 实现 [CacheProtection]：淘汰与维护清理在保护锁内读取保护集并删除，杜绝按过期快照误删新保护。
 * - 实现 [CacheLeaseRegistry]：供 [CacheOpenManager] 取得租约。
 *
 * tabId 与 cacheKey 关系仅为内存映射，不持久化；若需多端/进程安全请替换为持久化抽象。
 */
class TabCacheRegistry(
    private val cacheRoot: File
) : CacheProtection, CacheLeaseRegistry {

    private val lock = Any()
    private val refs = HashMap<String, Int>() // cacheKey -> 引用计数
    private val tabToCacheKey = HashMap<String, Pair<String, String>>() // tabId -> (contentType, cacheKey)

    override fun acquire(cacheKey: String, contentType: String): CacheLease {
        synchronized(lock) {
            refs[cacheKey] = (refs[cacheKey] ?: 0) + 1
        }
        return RegistryLease(cacheKey = cacheKey, contentType = contentType)
    }

    /** 标签接管：把已有租约归属到 tabId。返回 false 表示该 tab 已绑定或该 key 已无租约。 */
    internal fun transferToTab(cacheKey: String, contentType: String, tabId: String): Boolean =
        synchronized(lock) {
            if (tabToCacheKey.containsKey(tabId)) return false
            if (!refs.containsKey(cacheKey)) return false
            tabToCacheKey[tabId] = Pair(contentType, cacheKey)
            true
        }

    /**
     * 标签关闭：撤销归属引用；计数归零时才删除缓存目录。
     * 删除在保护锁内执行，避免「计数归零后、删除前」有新 acquire 建立保护却被误删。
     */
    suspend fun onTabClosed(tabId: String) {
        val pair = synchronized(lock) { tabToCacheKey.remove(tabId) } ?: return
        releaseRef(pair.second, pair.first)
    }

    /**
     * 释放一个引用；仅在计数归零时删除目录（删除与计数变更同锁）。
     */
    internal suspend fun releaseRef(cacheKey: String, contentType: String) {
        withContext(NonCancellable + Dispatchers.IO) {
            synchronized(lock) {
                val current = refs[cacheKey] ?: return@synchronized
                if (current > 1) {
                    refs[cacheKey] = current - 1
                    return@synchronized
                }
                refs.remove(cacheKey)
                // 不再保持标签归属：可见状态已由调用方先撤销
                cacheDir(contentType, cacheKey).deleteRecursively()
            }
        }
    }

    /** 当前所有活跃保护（在途租约 + 存活标签）的 cacheKey。 */
    fun activeCacheKeys(): Set<String> = protectedKeys()

    override fun protectedKeys(): Set<String> = synchronized(lock) { refs.keys.toSet() }

    override fun <T> withProtectionLock(block: () -> T): T = synchronized(lock) { block() }

    override fun deleteCacheDirIfUnprotected(cacheKey: String, dir: File): Boolean = synchronized(lock) {
        if (refs.containsKey(cacheKey)) return false
        dir.deleteRecursively()
    }

    private fun cacheDir(contentType: String, cacheKey: String): File =
        cacheRoot.resolve(contentType.lowercase()).resolve(cacheKey)

    private inner class RegistryLease(
        override val cacheKey: String,
        private val contentType: String
    ) : CacheLease {
        private var state = LEASE_STATE_HELD

        override fun transferToTab(tabId: String): Boolean = synchronized(this) {
            if (state != LEASE_STATE_HELD) return false
            if (!this@TabCacheRegistry.transferToTab(cacheKey, contentType, tabId)) return false
            state = LEASE_STATE_TRANSFERRED
            true
        }

        override suspend fun release() {
            val shouldRelease = synchronized(this) {
                if (state != LEASE_STATE_HELD) {
                    false
                } else {
                    state = LEASE_STATE_RELEASED
                    true
                }
            }
            if (shouldRelease) {
                this@TabCacheRegistry.releaseRef(cacheKey, contentType)
            }
        }
    }
}
