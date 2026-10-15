# MHTML 修复执行记录（`mhtml_fix_plan_2026-10-15.md`）

## 0. 基线与范围

- 计划来源：`reviews/mhtml_fix_plan_2026-10-15.md`；复核来源：`reviews/mhtml_current_review_2026-10-15.md`。
- 起始基线：`8617ad4`（计划文档），功能基线 `b2c8fe1`，版本 `1.0.71 / 72`。
- 仅处理 Q1-Q4。PDF、上传、备份/收藏契约、Hilt 启用均不在范围内。
- 未提交 V03（备份/网络凭据游标迁移/本地 rename 及测试）保持原样，未纳入本批提交。
- 构建使用 JDK 17。

## 1. 批次状态总览

| 批次 | 任务 | 状态 | 版本 / APK |
|---|---|---|---|
| A | T01 Q1 有效手势接管 | 已完成 | 1.0.72 / 73 |
| B | T02 Q4 缓存完整性 | 已完成 | 1.0.73 / 74 |
| C | T03 Q2/Q3 缓存协调 | 已完成 | 1.0.74 / 75 |
| D | T04 总回归与交接 | 进行中 | — |

## 2. 批次 A（T01：Q1 有效手势接管）

### 2.1 问题

旧实现把任意 `ACTION_MOVE` 当作主动接管：不校验 down 坐标、位移阈值、手势取消与内容尺寸，
轻触抖动即可把 `webRestoreInteractionGeneration` 置为当前代次，使恢复流程停止定位并放行采样，
`stopAndFlush` 随后把当前位置（仍为 0）尾写覆盖旧进度（history 0.6 → 0.0）。

### 2.2 变更

- 新增 `core/reader/web/WebGestureTakeoverState.kt`：平台无关状态机。
  - `onDown` 记录按下坐标与内容 generation；
  - `onMove` 仅当位移平方超过 `touchSlop²` 且内容尺寸有效时返回 true；
  - generation 不一致或尺寸无效时不解锁，但保留手势以便后续有效位移累计；
  - `clear` 在 ACTION_CANCEL / ACTION_UP 清理。
- `app/src/main/java/html_reader/ReaderFragment.kt`：
  - 触摸监听器抽取为 `handleWebPreviewTouch(event)`（生产路径），DOWN/MOVE/CANCEL/UP 分别处理；
  - `webTouchSlop` 由 `ViewConfiguration.get(context).scaledTouchSlop` 提供，不再使用固定 1px；
  - `markCurrentWebContentTakenOver()` 只在有效拖动时置位并放行采样；
  - 仍返回 `false`，不消费触摸事件（点击链接/缩放/长按不受影响）；
  - `loadSelectedWebTab`、`invalidateWebContent` 增加 `webGestureTakeover.clear()`。

### 2.3 回归

- `app/src/test/java/com/html_reader/WebGestureTakeoverStateTest.kt`：slop 阈值、无 DOWN、跨代次、尺寸无效延后、clear 六项。
- `app/src/test/java/com/html_reader/ReaderFragmentGestureTakeoverTest.kt`：通过反射取回 Fragment 安装的生产 `OnTouchListener` 驱动触摸序列，使用真实 Room/Writer/Tracker：
  - 轻触（<slop）后 progress 保持 0.6；
  - 有效拖动后接管并写入新位置 1.0，不因旧进度回拉；
  - 内容零尺寸时拖动不接管、不放行采样；
  - DOWN 后 CANCEL 再 MOVE 不接管。

### 2.4 测试统计

- `:core-base:testDebugUnitTest :app:testDebugUnitTest`：`app 240（2 跳过）+ core-base 23 = 263`，failures 0 / errors 0 / skipped 2。

### 2.5 交付

- 版本 `1.0.72 / 73`；APK：`app/build/outputs/apk/debug/MHTMLReader_v1.0.72_debug.apk`
  - SHA256 `A115C5BAD1F1325319A027FC7804B00A375436DD774C6C15A5787E75BEECCCC3`

### 2.6 设备待验（未执行）

- 真机轻触/抖动/长按图片/缩放不误覆盖或回拉；后台返回正常。
- Robolectric 不能替代真实 Chromium 触摸分发与 TalkBack 验证。

## 3. 批次 B（T02：Q4 缓存完整性）

### 3.1 问题

`CacheOpenManager.openToCache` 把 `totalBytes: Long` 当已知长度使用，且直接写入最终
`content.[ext]`；EOF 后无条件上报 `CopyProgress(totalBytes, totalBytes)`，从不比较实际
copied。TabManager 先采样本地文件长度、再打开输入流，源在打开前被截短或拷贝期间原地截短时
read 正常返回 EOF，于是缓存里只剩前 60 字节却上报 100/100 并发布 Ready 标签（Q4）。

### 3.2 变更

- `core/cache/model/CopyProgress.kt`：`totalBytes` 改为 `Long?`（`null` 未知、`0` 已知空文件）。
- `core/reader/model/OpenState.kt`：`Copying.totalBytes` 同步为 `Long?`。
- `core/cache/CacheOpenManager.kt`：
  - 签名 `totalBytes: Long?`；`generateCacheKey(size: Long?)` 与之一致（`versionStamp` 非空时键不变）。
  - 先写本次专属 `content.[ext].part`，关闭输出后再校验实际字节数；
  - 校验通过才 `delete + rename` 提交为最终成品；失败/取消只删除本次 `.part`；
  - 已知长度不相等即失败：截短报 `AppError.IoError("Incomplete copy: expected=… actual=…")`，
    增长（`copied + read > totalBytes`）抛 `EOFException("Source length changed while copying")`，两者都不会落入末尾假成功；
  - 每块写入前 `ensureActive()` 并做长度检查；
  - 上报真实 copied；缓存命中只命中已提交成品；Windows 下先关闭句柄再删临时文件（否则删除失败）；
  - CancellationException 直接重抛、取消态不再 emit；`flush/close` 失败走失败路径而非 finally 吞掉。
- `core/reader/tab/DefaultReaderTabManager.kt`：本地已存在文件取真实长度（0 为已知空文件），
  SAF/网络等未知长度传 `null`；建 Ready 前要求成品存在且已知长度时长度一致。
- `core/domain/usecase/ObserveDisplayTitlesUseCase.kt`：仅正数视为已知长度，其余按未知处理；
  `buildVersionStamp` 取值不变，避免标题缓存无谓失效。
- `app/src/main/java/html_reader/ReaderFragment.kt`：`Copying` 的 `totalBytes == null` 或 `<= 0` 时按不确定进度展示。

### 3.3 回归

- `app/src/test/java/com/html_reader/CacheOpenManagerIntegrityTest.kt`（7 项）：
  截短 100→60 报错且无半成品；100/100 成功并上报真实字节；增长 100→140 不假成功；
  已知空文件提交 0 字节成品；未知长度接受实际字节且 `totalBytes` 为 null；
  失败不损坏其他持有者的有效缓存；取消清理半成品且从不提交。
- `app/src/test/java/com/html_reader/DefaultReaderTabManagerTest.kt` 新增：
  真实 `LocalFileSystem` + TabManager 链路，打开输入流前原地截短到 60，
  断言无 Ready、有 Error、无 `content.*` 半成品、无 tab 建立（正式串联，而非仅断言 helper）。

### 3.4 测试统计

- `:core-base:testDebugUnitTest :app:testDebugUnitTest`：`tests=271 failures=0 errors=0 skipped=2`（较 T01 基线 263 新增 8 项）。

### 3.5 交付

- 版本 `1.0.73 / 74`；APK：`app/build/outputs/apk/debug/MHTMLReader_v1.0.73_debug.apk`
  - SHA256 `21C96A09D25DF1C7651683125CB79FE8F572FB250AD18BE7A03A91F160B95BA5`

### 3.6 未闭合 / 下一步

- 设备待验（未执行）：本地/SMB/FTP 打开 MHTML，拷贝期间中断源文件后应显示失败而非"完成"后空白；
  FTP/SMB 未知长度源（`totalBytes=null`）仍可正常打开。
- 未知长度源无法检测同长度内容变化（长度检查本身不证明 MHTML 语法有效），属计划已声明的边界。

## 4. 批次 C（T03：Q2/Q3 缓存协调）

### 4.1 所有权说明（C0，先于改代码）

**① 唯一协调对象与共享方式**

- 每个 `cacheRoot` 由 `ReaderRuntime.ensure()` 创建且仅创建一次两个对象，并注入唯一的 `CacheOpenManager`：
  - `TabCacheRegistry`：**保护与租约唯一来源**（引用计数、标签归属、受保护删除、保护锁）。
  - `CacheEvictor`：**容量预算唯一来源**（预约登记 + 淘汰），持有 `CacheProtection` 引用（即上面的 registry）以便“检查-删除”原子化。
- `CacheOpenManager(cacheRoot, ..., cacheEvictor, activeKeysProvider, leaseRegistry)` 是**唯一拷贝入口**；`leaseRegistry` 传同一 `TabCacheRegistry`，`cacheEvictor` 传同一 `CacheEvictor`。
- 维护入口（`HtmlReaderApp.cleanupOrphanCacheAtStartup`、`AppOrphanCacheCleanupWorker`、`DefaultMaintenanceManager`）同样取 `ReaderRuntime.tabCacheRegistry(context)` 作为 `CacheProtection`，保证“打开”与“清理”共用同一保护来源，不再各拿一份 `activeCacheKeys` 快照。
- 未启用 Hilt；`core/di`、`core/*/di` 的 provider 仅同步签名保持可编译，运行时仍走 `ReaderRuntime`。

**② 各阶段 lease 持有者**

| 阶段 | 持有者 |
|---|---|
| 缓存命中（成品存在） | `CacheOpenManager` 立即 `registry.acquire(cacheKey)`，随成功帧交给调用方 |
| 拷贝开始 | `CacheOpenManager` 在预约前 `acquire(cacheKey)`（替代原 `inFlightKeys`，成为唯一保护来源） |
| 历史查询 / 建 tab / 去重判定 | 仍由 `DefaultReaderTabManager` 持有（Flow 结束**不自动撤销**待移交保护） |
| 发布 tabs/current | `DefaultReaderTabManager` 在 `synchronized(tabStates)` 内**先** `lease.transferToTab(tabId)`，**再** publish `_tabs`/`_currentTabId` |
| 去重命中已有 tab | `DefaultReaderTabManager` 的 `finally` 释放本次多余 lease（`releaseIfNotTransferred`），不动已有 tab 的缓存 |
| 历史读取失败 / 取消 / 长度校验失败 / 提前返回 | 同上 `finally` 释放未移交 lease |
| 标签存活期 | `TabCacheRegistry`（tabId→cacheKey 映射 + 计数） |
| 关闭标签 | `DefaultReaderTabManager.closeTab`：先在同步区内移除 `tabStates` 并 publish，**最后**才 `registry.onTabClosed(tabId)`；计数归零才删目录 |

**③ 预算核算（避免双计/漏计）**

- 记账口径：对每个 cacheKey 目录，**若有在途预约则按预约字节计入**，否则按盘上实际大小计入；无对应目录的预约单独累加。散落文件（无 key）按盘上大小计入。
- 不变量：**（按上述口径的）受管理已提交字节 + 在途预约 ≤ maxBytes**。因在途预约按“预约值”而非“已落盘值”计入，写入中的 `.part` 不会与预约重复计数。
- 已知长度：首块前一次性 `reserve(totalBytes)`；`totalBytes >= maxBytes` 直接 `EXCEEDS_LIMIT`（保持“文件大小等于上限也拒绝”）。
- 未知长度：按块 `growReservation(needed)`，首次精确、之后几何增长（`min(2×current, maxBytes-1)`）以减少扫描；写入前扩容，保证预约 ≥ 已落盘字节。
- 提交：`renameTo` 成功后 `releaseReservation(cacheKey)`——此刻盘上已是最终文件，按盘上大小计入，口径无缝切换（不先释放再登记）。
- 失败/取消：删除 `.part` 后 `releaseReservation`。
- 删除失败（`deleteRecursively` 返回 false，含 Windows 句柄占用）不计入已释放空间 → 继续尝试其它候选，最终返回 `CANNOT_FREE`（映射 `CacheBusy`）。
- `FTP/SMB` 中转区另占用磁盘；`maxBytes` 仅约束 `app_cache`，不宣称是整机磁盘硬上限。

**④ 锁、挂起、幂等与顺序**

- 两把锁，固定加锁顺序 `budgetLock(Mutex)` → `protectionLock(synchronized)`，仅 `reserve/growReservation/makeRoomFor/evictIfNeeded` 同时持两把；`evictOldFiles`、`acquire`、受保护删除、`releaseRef` 只持 `protectionLock`。
- 挂起边界：`reserve/growReservation/release/makeRoomFor/evictOldFiles` 为 `suspend`（内部 `withContext(Dispatchers.IO)`）；`acquire/transferToTab/withProtectionLock/deleteCacheDirIfUnprotected` 为普通函数（不挂起）。**全局锁只在记账与删除期间持有，绝不覆盖网络/流拷贝全程**（拷贝使用已取得的流句柄，不持锁）。
- 幂等：`CacheLease.transferToTab`/`release` 只生效一次；`releaseReservation` 对不存在键为空操作；`onTabClosed` 对未知 tab 为空操作。
- 关闭/删除顺序：可见状态（`tabStates`/`_tabs`/`_currentTabId`）先撤销 → `registry.onTabClosed` → 计数归零才删除目录；删除在 `protectionLock` 内执行，避免“计数归零后、删除前”有新 `acquire` 建立保护却被误删。
- 淘汰/清理顺序：`protection.withProtectionLock` 内完成“保护集快照 + 删除”，杜绝“先快照、后建保护、再按过期快照删除”。
- 在途锁表：`copyLocks` 仍**不回收**（如实记录边界：进程内长期打开的 cacheKey 会保留一个 `Mutex` 条目，数量受历史打开数限制；回收需处理等待者，本批不做）。

### 4.2 C1/C2 变更

- 新增 `core/cache/CacheLease.kt`：租约接口（`cacheKey` / `transferToTab` / `release`，幂等）。
- 新增 `core/cache/CacheProtection.kt`：保护唯一来源接口（`protectedKeys` / `withProtectionLock` / `deleteCacheDirIfUnprotected`）＋ `NONE` 默认实现。
- `core/cache/TabCacheRegistry.kt`：改为引用计数租约 + 标签归属 + 受保护删除，实现 `CacheProtection` 与 `CacheLeaseRegistry`；`activeCacheKeys()` 保留（= 当前租约键）。
- `core/cache/CacheEvictor.kt`：新增预约预算（`reserve` / `growReservation` / `releaseReservation`）与 `budgetLock`；`makeRoomFor`/`evictIfNeeded`/`evictOldFiles` 走 `protection` 原子删除，且跳过预约键。
- `core/cache/CacheOpenManager.kt`：删除 `inFlightKeys`（保护下沉到租约）；拷贝前 `acquire` 租约 + `reserve` 预约；成功帧携带租约；失败/取消 `NonCancellable` 释放。
- `core/cache/model/CopyProgress.kt`：新增 `lease: CacheLease? = null`（仅最终成功帧非空）。
- `core/reader/tab/DefaultReaderTabManager.kt`：`openNewTab` 捕获租约并在同步区内先移交再发布；`finally` 释放未移交租约；`closeTab` 改为“先撤销状态、后释放引用”。
- `core/cache/OrphanCacheCleaner.kt`：新增 `protection` 参数，目录删除走 `deleteCacheDirIfUnprotected`。
- 接线：`ReaderRuntime`（传 `leaseRegistry`、共享 `CacheEvictor` 与 registry）、`HtmlReaderApp`、`AppOrphanCacheCleanupWorker`、`core/di/CoreModule`、`core/domain/usecase/ObserveDisplayTitlesUseCase.kt`（用后释放租约）。
- `DefaultMaintenanceManager`、`core/reader/di/ReaderModule`、`core/maintenance/di/MaintenanceModule` **未修改**：前者运行时未接线（Hilt 未启用）、不在生效链路；后两者现有 provider 签名与新构造兼容，仅同步了 `CoreModule` 的参数。
- `CacheEvictor` 默认参数 `protection = CacheProtection.NONE`，故既有未改签名的调用方（含 `CacheEvictorTest`）行为不变。

### 4.3 C1/C2 回归（受控并发，正式回归而非探针）

新增 `app/src/test/java/com/html_reader/CacheCoordinationTest.kt`（9 项）。全部用 `CompletableDeferred` 屏障建立确定性时序，无 `Thread.sleep`、无概率重跑：

- Q3 `concurrentReservations_onEmptyBudget_onlyOneGranted`：空预算 200，A/B 各 150 在首块前同时预约，仅 A 获准并成功；B 报 `CacheBusy`；磁盘总量 ≤ 200（不出现 300）；小额探针 `reserve(40)` 为 `READY` 证明 B 预约已归还。
- Q2 `cacheProtectedWhileAwaitingHistory_thenBecomesReady`：A 拷贝完成、成品落盘后挂在历史查询；B 需 150 只能删除 A 缓存，受保护故失败（`CacheBusy`）；A 最终 `Ready` 且成品仍在。
- `dedup_reusesTab_andCloseDeletesOnlyWhenNoRefLeft`：同路径同版本复用同一 tab、不增加引用、不删缓存；关闭最后一个 tab 后成品才被删除、保护集清空。
- `concurrentSameKeyOpen_doesNotLeakExtraReference`：A 挂起时 B 先行建 tab，A 落入重复分支释放多余租约；最终仅 1 个 tab、1 个引用。
- `cancelDuringCopy_releasesReservationAndLease`：拷贝中取消后保护集清空、预约归还（900 预约成功）。
- `unknownLength_respectsBudget_andRecoversAfterFailure`：未知长度 400 超预算 300 报 `CacheLimitExceeded` 且无残留；预算归还后较小未知长度成功。
- `maintenanceCleanup_doesNotDeleteProtectedFile`：`evictOldFiles` 不删受保护目录，仅删过期未受保护项。
- `deleteFailure_isReportedAsCannotFree`：删除失败（返回 false）时报 `CANNOT_FREE` 且文件保留。
- `readerRuntime_sharesSingleRegistryAndProtectsOpenTab`：经 `ReaderRuntime` 打开 MHTML，验证 `tabCacheRegistry` 为单例且打开后在同一 registry 登记保护、关闭后释放——**接线验收**，非仅测手工 new 的 helper。

同步改造既有 4 个测试文件构造签名：`CacheOpenManagerIntegrityTest`、`CacheOpenManagerFailureTest`、`CacheOpenManagerInFlightTest`、`DefaultReaderTabManagerTest`（改用共享 `TabCacheRegistry` 注入 `leaseRegistry`/`protection`）。`CacheEvictorTest` 未改（走 `CacheProtection.NONE`）。

### 4.4 测试统计（批次 C 交付时）

- 命令：`.\gradlew.bat :core-base:testDebugUnitTest :app:testDebugUnitTest`
- 结果：`files=55 tests=280 failures=0 errors=0 skipped=2`（较 T02 基线 271 新增 9 项）。
- 跳过原因：`LocalFileSystemRenameTest` 中 2 项符号链接用例在 Windows 无创建权限，按 `Assume` 跳过；属未提交 V03 文件，与批次 C 无关。

### 4.5 交付

- 版本 `1.0.74 / 75`；APK：`app/build/outputs/apk/debug/MHTMLReader_v1.0.74_debug.apk`
  - SHA256 `BF70F2F4F0A8A476FF298B7E1EF48CCC2A1EDBDCD712D647837B8D28EC1ECE96`
  - 大小 23,398,447 字节
- 工作区仍含未提交 V03 文件（`core/backup/*`、`core/data/repo/NetworkConfigRepository.kt`、`core/database/dao/NetworkConfigDao.kt`、`core/vfs/local/LocalFileSystem.kt` 及对应测试、`reviews/review_answers_504583f_2026-10-12.md`），本批未纳入提交。

### 4.6 未闭合 / 下一步

- 设备待验（未执行）：见批次 D 清单；Robolectric 不能替代真实设备签字。
- 在途锁表 `copyLocks` 不回收，进程内长期打开过的 cacheKey 保留一个 `Mutex` 条目，数量受历史打开数限制（已记录边界，回收需处理等待者，本批不做）。

