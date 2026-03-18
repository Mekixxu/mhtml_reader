# Disk Cache Management and Eviction Spec

## Why

1. **Crash Prevention**: Opening large files (1GB+) can fill up storage, potentially leading to crashes or failures.
2. **User Requirement**: The user explicitly requested a "Download to local first" strategy with a specific eviction policy (Max 15GB, Max 7 days) to manage storage and "memory" (interpreted as storage capacity).
3. **Performance**: Proactive eviction ensures space is available before attempting large downloads/copies.

## What Changes

### Cache Eviction Logic (`CacheEvictor.kt`)

* **Add Age Limit**: Implement logic to delete files older than 7 days.

* **Add Capacity Check**: Implement `makeRoomFor(bytes: Long)` to evict files based on LRU until enough space is available.

* **Update Trigger**: Ensure eviction runs *before* adding new files to the cache, not just on startup.

### Cache Management (`CacheOpenManager.kt`)

* **Integration**: Inject `CacheEvictor` into `CacheOpenManager`.

* **Pre-check**: Before starting a copy, call `evictor.makeRoomFor(fileSize)`.

### Configuration (`ReaderRuntime.kt`)

* **Limits**: Set Cache Size Limit to 15GB.

* **Retention**: Set Max Age to 7 days.

## Impact

* **Stability**: Reduces disk-full errors.

* **Storage**: App will use up to 15GB of space but will self-clean.

* **Code**: `CacheOpenManager` now depends on `CacheEvictor`.

## Implementation Details

### CacheEvictor

```kotlin
fun evictOldFiles(maxAgeMs: Long)
fun makeRoomFor(requiredBytes: Long)
```

### CacheOpenManager

```kotlin
// In openToCache
cacheEvictor.evictOldFiles(7 * 24 * 3600 * 1000L)
cacheEvictor.makeRoomFor(totalBytes)
```

### Limits

* 15GB = `15L * 1024 * 1024 * 1024`

