# Tasks

- [x] Task 1: Enhance CacheEvictor <!-- id: 0 -->
    - Implement `evictOldFiles(maxAgeMs: Long)`.
    - Implement `makeRoomFor(requiredBytes: Long)`.
    - Update `evictIfNeeded` to use the new logic.
- [x] Task 2: Integrate Eviction in CacheOpenManager <!-- id: 1 -->
    - Inject `CacheEvictor` into `CacheOpenManager`.
    - Call eviction methods before file copy in `openToCache`.
- [x] Task 3: Configure Limits in Runtime <!-- id: 2 -->
    - Update `ReaderRuntime` to set 15GB limit and 7-day retention.
- [x] Task 4: Verify Cache Behavior <!-- id: 3 -->
