package core.cache

/**
 * 一次「打开缓存」尝试所持有的保护租约。
 *
 * 生命周期：拷贝开始或缓存命中时取得，持续到「移交给标签」或「调用方显式释放」为止。
 * 只要租约仍被持有（引用计数 > 0），对应 cacheKey 的缓存目录就不会被淘汰或维护清理删除。
 *
 * 移交与释放都幂等，且只生效一次：
 * - [transferToTab] 成功后，保护所有权转由标签持有（标签关闭时释放）；
 * - [release] 仅在「尚未移交」时生效，用于去重、失败、取消等放弃路径。
 */
interface CacheLease {
    val cacheKey: String

    /** 把保护所有权移交给标签；返回 false 表示该租约已移交或已释放（幂等，不重复计数）。 */
    fun transferToTab(tabId: String): Boolean

    /** 释放尚未移交的保护；已移交时为空操作。幂等。 */
    suspend fun release()
}

/**
 * 租约获取入口。`TabCacheRegistry` 是运行时的唯一实现。
 */
fun interface CacheLeaseRegistry {
    fun acquire(cacheKey: String, contentType: String): CacheLease
}
