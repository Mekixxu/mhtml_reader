# mhtml_reader 全项目代码审查报告

> - 审查模型：claude_sonnet4
> - 审查日期：2026-08-16
> - 审查对象：mhtml_reader Android 项目（全量，7 模块）

---

## 0. 审查范围与方法

| 维度 | 说明 |
|---|---|
| 范围 | 全部 7 个模块（app / core-base / core-storage / core-data / core-domain / feature-files / feature-reader），60+ 源文件、全部 `build.gradle.kts`、Manifest、`gradle.properties` |
| 方法 | 四维度审查（架构 / Kotlin / 性能 / 安全）+ 两个独立验证子代理交叉复核 |
| 交叉验证 | **21 条问题 100% 确认存在，0 条误报**（两验证者独立给出，含行号微调与严重度差异，下文已采纳更严者） |
| 编译验证 | 尝试 `:app:compileDebugKotlin`，被沙箱拦截（`trae-sandbox` 拒绝访问 `Android SDK/.knownPackages`），非代码错误；孤儿目录本就不参与编译，不会触发编译失败 |

---

## 1. 意图推断

项目意图清晰：**把原来的单体 app 重构为多模块工程**（`app` + `core-*` + `feature-*`），并引入现代 Android 组件栈（Hilt、Room 2.6、WorkManager、DataStore、kotlinx-serialization），同时保留三套手动运行时装配（`CoreRuntime`/`FilesRuntime`/`ReaderRuntime`）作为兜底。

但"拆分"是**软拆分**：通过 `sourceSets.main.java.setSrcDirs(...)` 把 `core/` 下分散目录虚拟映射进各模块，导致部分目录（`core/favorites`、`core/settings`、`core/network`、`core/work`、`core/maintenance`、`core/di`）**不属于任何模块、完全未编译**。整个 Hilt DI 体系与基于设置的维护调度体系形同虚设。架构演化中途停止，留下大量"已设计未接线"的死代码与双轨实现。

---

## 2. 变更概览（技术流程图 ×2）

### 图 1：构建装配与孤儿目录问题链路（I3、I12）

```mermaid
flowchart TD
    A["build.gradle.kts<br/>sourceSets.setSrcDirs(...)"] --> B["已映射目录<br/>common/vfs/database/repo/cache/title/backup/fileops/files/reader"]
    A --> C["孤儿目录（未编译）<br/>core/favorites · core/settings · core/network<br/>core/work · core/maintenance · core/di"]

    B --> D["7 模块参与编译<br/>实际可运行"]
    C --> E["Hilt @Module/@Provides 全部失效"]
    C --> F["DefaultMaintenanceManager<br/>DefaultWorkScheduler<br/>MaintenanceWorker 从不执行"]
    C --> G["SettingsDataStore 无消费者"]

    E -. "未注册" .-> H["运行时真实生效的是："]
    F -. "被绕过" .-> H
    H --> H1["HtmlReaderApp.scheduleMaintenanceWorkers<br/>硬编码 maxItems=500/maxDays=365/daysUnused=3"]
    H --> H2["cleanupOrphanCache() 启动即执行"]

    style C fill:#ffcdd2,color:#b71c1c
    style H fill:#fff3e0,color:#e65100
    style H1 fill:#fff3e0,color:#e65100
    style H2 fill:#fff3e0,color:#e65100
    style E fill:#ffcdd2,color:#b71c1c
    style F fill:#ffcdd2,color:#b71c1c
    style G fill:#ffcdd2,color:#b71c1c
```

### 图 2：数据安全与缓存失效链路（I1、I2、I4、I18）

```mermaid
flowchart TD
    DB["AppDatabase version=4<br/>新增 folder_sessions 表"] --> MIG["仅有 MIGRATION_2_3"]
    MIG --> GAP["缺失 3→4 迁移"]
    GAP --> FALLBACK["fallbackToDestructiveMigration()<br/>v3→v4 升级清空用户数据"]

    CRED["NetworkConfigEntity.password 明文"] --> URL["buildFtpUrl 凭据嵌入 URL"]
    CRED --> BAK["JsonBackupManager 明文导出 JSON"]
    CRED --> BUP["android:allowBackup=true<br/>备份可恢复含密码数据"]

    DL["FilesTransferGateway<br/>ftp_open / smb_open 目录"] --> LEAK["无任何清理代码<br/>CacheEvictor/OrphanCacheCleaner 均不覆盖"]
    ORPHAN["OrphanCacheCleaner<br/>按 lastModified 删除"] --> HIT["不感知 TabCacheRegistry<br/>误删活动 tab 缓存 → file not found"]

    style GAP fill:#ffcdd2,color:#b71c1c
    style FALLBACK fill:#ffcdd2,color:#b71c1c
    style URL fill:#ffcdd2,color:#b71c1c
    style BAK fill:#ffcdd2,color:#b71c1c
    style LEAK fill:#ffcdd2,color:#b71c1c
    style HIT fill:#ffcdd2,color:#b71c1c
```

---

## 3. Phase 1：架构评估

### 3.1 总体概述

分层意图明确（UI → feature → core-domain/storage/data/base），View 层职责划分清楚（`FilesUiBinder`/`FilesStartupHandler`/`FilesSessionPlanner` 等辅助类拆分值得肯定）；协程贯穿 I/O，`DispatcherProvider` 统一封装调度器；Room DAO 使用 `Flow`/`suspend`，符合现代做法；`DefaultReaderTabManager` 通过 `TabCacheRegistry` 绑定 tab 与缓存，设计方向正确。

### 3.2 主要优点

1. **数据流方向清晰**：`ReaderViewModel` 仅做状态委托，`ReaderTabManager` 统一收敛"缓存复制 → 进度恢复 → tab 建立 → 历史记录"，链路完整。
2. **WebView 安全边界有明确策略**：JS 默认关闭、`MIXED_CONTENT_NEVER_ALLOW`、`BlockingResourceWebViewClient` 白名单拦截，意图正确。
3. **缓存组织规范**：按 `contentType/cacheKey/content.ext` 三层目录组织，cacheKey 纳入 contentType+路径+版本戳，可调试性良好。
4. **标题提取与编码探测较完整**：`HtmlTitleExtractor` 覆盖 UTF-8/GBK/ISO-8859-1 + Quoted-Printable，属少见的高完成度实现。

### 3.3 核心劣势

1. **软拆分导致"影子架构"**：六个目录不参与编译，Hilt/维护体系是死代码；同一包存在两个 `IFileSystem` 接口（`core/vfs/IFileSystem.kt` 与 `core-storage/src/main/java/core/vfs/` 下的副本），编译期与源码树不一致。
2. **Room 迁移缺失 + 无条件 destructive fallback**：v3→v4 升级直接清库，这是当前最高优先级的数据风险。
3. **手工导航状态机 + 魔法字符串**：`MainActivity` 用 hide/show + tag 字符串管理 Fragment，配置变化与返回栈行为脆弱。
4. **三套 DI 并存**：Hilt 注解、手工 Runtime 单例、直接 new —— 同一依赖可有三种获取方式，可维护性差。

---

## 4. Phase 2：深挖问题清单（按严重度）

### 🔴 CRITICAL / HIGH（8 条）

---

**I1. Room 迁移缺失，v3→v4 升级清空用户数据** — CRITICAL · 数据

位置：[AppDatabase.kt](../core/database/AppDatabase.kt#L16-L40) 与 [CoreRuntime.kt](../app/src/main/java/html_reader/CoreRuntime.kt#L25-L28)

机制：`version = 4` 新增 `FolderSessionEntity`，但 companion 中仅注册 `MIGRATION_2_3`；`CoreRuntime` 无条件 `fallbackToDestructiveMigration()`。已安装 v3 用户升级后，favorites/history/network_configs 全部清空。

```kotlin
// Before
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE network_configs ADD COLUMN encoding TEXT NOT NULL DEFAULT 'Auto'")
    }
}
```

```kotlin
// After —— 补上 3→4 迁移并在 CoreRuntime 注册
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `folder_sessions` (
               `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
               `name` TEXT NOT NULL,
               `rootPath` TEXT NOT NULL,
               `currentPath` TEXT NOT NULL,
               `createdAt` INTEGER NOT NULL,
               `lastAccess` INTEGER NOT NULL)"""
        )
    }
}
// CoreRuntime: .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
```

---

**I2. 网络凭据明文存储并在多链路泄露** — CRITICAL · 安全

位置：[NetworkConfigEntity.kt](../core/database/entity/NetworkConfigEntity.kt#L23)（文件头注释自认"仅限 v1.0，生产必须加密"）、[FilesNetworkGateway.kt](../app/src/main/java/html_reader/files/FilesNetworkGateway.kt#L50-L61)（`ftp://user:pass@host` 凭据进 URL）、[JsonBackupManager.kt](../core/backup/JsonBackupManager.kt#L101) 与 [L212-L221](../core/backup/JsonBackupManager.kt#L212-L221)（明文导入导出）、[AndroidManifest.xml](../app/src/main/AndroidManifest.xml#L11)（`allowBackup=true` 使备份包含密码）。

机制：FTP 密码出现在 URL 字符串中（可被日志/调试器/Android 历史捕获）；JSON 备份为明文文件；系统备份进一步扩大暴露面。

```kotlin
// After —— 优先用 Android Keystore + EncryptedSharedPreferences 方案：
// 1) 数据库仅存密文（AES-GCM），密钥存 Keystore 不可导出；
// 2) FTP/SMB 连接改为通过 NtlmPasswordAuthenticator / JcifsContext 显式传入凭据，
//    而非拼进 URL；
// 3) 备份导出时对 password 字段做字段级加密或打码；
// 4) Manifest 增加 android:allowBackup="false" 或 dataExtractionRules 白名单。
```

---

**I3. 六个目录未参与编译，Hilt/维护体系整体失效** — HIGH · 架构

位置：各模块 [build.gradle.kts](../core-base/build.gradle.kts#L23-L31) 的 `setSrcDirs` 映射。

机制：`core/favorites`、`core/settings`、`core/network`、`core/work`、`core/maintenance`、`core/di` 不在任何模块 srcDirs。`DefaultMaintenanceManager`/`DefaultWorkScheduler`/`MaintenanceWorker`/`SettingsDataStore`/收藏树模型全部不可达；`app/` 内 `import core.(favorites|settings|network|work|maintenance|di).` 零匹配（Grep 证实）。

```kotlin
// After —— 二选一：
// A) 最小改动：把以上目录并入现有模块的 srcDirs（如 core-domain 挂 cache/domain/title/backup，
//    再把 work/settings/di 挂 core-domain 或 core-data）；
// B) 根治：放弃 setSrcDirs 软拆分，把目录按真实依赖迁移到各模块 src/main/java 下，
//    删除核心目录的虚拟映射。
```

---

**I4. FTP/SMB 下载缓存永久泄漏** — HIGH · 存储

位置：[FilesTransferGateway.kt](../app/src/main/java/html_reader/files/FilesTransferGateway.kt#L28)（`ftp_open`）与 [L50](../app/src/main/java/html_reader/files/FilesTransferGateway.kt#L50)（`smb_open`）。

机制：下载文件写入 `cacheDir/ftp_open`、`cacheDir/smb_open`，全仓库无任何清理；`CacheEvictor`/`OrphanCacheCleaner` 根目录均为 `cacheDir/app_cache`，不覆盖这两目录。每次远程打开文件都会留下永久副本，长期累积膨胀。

```kotlin
// After —— 打开完成后主动清理或纳入统一缓存治理：
// 方案A：Read-only 场景下改用 CacheOpenManager 统一链路（复用 app_cache 淘汰策略）；
// 方案B（临时）：阅读器关闭该 tab 后删除对应文件，并注册一个定期兜底清理。
```

---

**I5. `DefaultReaderTabManager` 的 `tabStates` 跨线程竞争** — HIGH · 并发

位置：[DefaultReaderTabManager.kt](../core/reader/tab/DefaultReaderTabManager.kt#L41-L155)

机制：`tabStates` 是裸 `LinkedHashMap`，`openNewTab` 经 `flowOn(io)` 在 IO 线程写（L130），`closeTab`（L151）/`switchTo` 在 Fragment 主线程读写。并发下可能丢失 tab 或读到不一致快照。第 [110](../core/reader/tab/DefaultReaderTabManager.kt#L110) 行还残留"需要你在 repo 增加该方法"的占位注释。

```kotlin
// After —— 单一写线程 + 不可变快照
private val tabStates = kotlinx.coroutines.sync.Mutex()
private val _tabStateMap = mutableMapOf<String, ReaderTab>() // 仅在 Mutex 保护下访问

override suspend fun closeTab(tabId: String) {
    withContext(dispatcherProvider.io) {
        tabCacheRegistry.onTabClosed(tabId)
        tabStates.withLock {
            _tabStateMap.remove(tabId)
            _tabs.value = _tabStateMap.values.toList()
        }
    }
    // 切换 currentTabId 也统一在 withLock 内完成，避免跨线程读
}
```

---

**I10. 图片保存无大小上限，可致 OOM** — HIGH · 安全/健壮性

位置：[ReaderFragment.kt](../app/src/main/java/html_reader/ReaderFragment.kt#L542-L559)（`readBytes()` 全量读入内存）与 [L561-L582](../app/src/main/java/html_reader/ReaderFragment.kt#L561-L582)（`Base64.decode` 无限制）。

机制：恶意/异常 Web 页长按图片保存，`HttpURLConnection.inputStream.readBytes()` 或超大 data: URL 一次性加载进内存，`Bitmap` 解码在 `largeHeap` 下仍可能 OOM。

```kotlin
// After —— 流式下载 + 大小护栏 + 流式解码
private suspend fun resolveHttpPayload(source: String): ImagePayload {
    val connection = (URL(source).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000; readTimeout = 15_000; doInput = true
    }
    connection.connect()
    val total = connection.contentLengthLong
    if (total > MAX_IMAGE_BYTES) throw IllegalStateException("image too large: $total")
    val bytes = withContext(Dispatchers.IO) {
        connection.inputStream.use { it.readBytes(MAX_IMAGE_BYTES + 1) } // 超限抛异常
    }
    // 并对 data: URL 先校验 payload.length 再 Base64.decode
}
```

---

**I12. 维护任务重复调度且参数不一致** — HIGH · 架构（双轨）

位置：[HtmlReaderApp.kt](../app/src/main/java/html_reader/HtmlReaderApp.kt#L27-L63)

机制：`scheduleMaintenanceWorkers()` 硬编码 `maxItems=500/maxDays=365/daysUnused=3`（与设计中的 Settings 驱动策略相悖），且 `cleanupOrphanCache()` 启动即全量执行一次，与每日 Worker 重复跑。

```kotlin
// After —— 收敛为单一调度源：
// 1) 将维护参数改为从 SettingsDataStore 读取（或至少在 Worker 内按 inputData 唯一驱动）；
// 2) 删除启动时立即执行的那份，避免首启扫描 + 每日任务重复；
// 3) 若决定启用 DefaultWorkScheduler，则删除此处硬编码，二选一。
```

---

**I18. `OrphanCacheCleaner` 可能误删活动 tab 缓存** — HIGH · 正确性

位置：[OrphanCacheCleaner.kt](../core/cache/OrphanCacheCleaner.kt#L10-L27)

机制：仅按 `lastModified < cutoff` 删除 `app_cache` 下非空目录，不感知 `TabCacheRegistry` 中正在打开的 `tabId→cacheKey`。活动 tab 缓存超 3 天未触碰即被删，随后 `ReaderFragment` 报 `reader_file_not_found`。

```kotlin
// After —— 清理前排除活跃 cacheKey：
suspend fun clean(tabCacheRegistry: TabCacheRegistry) = withContext(Dispatchers.IO) {
    val activeKeys = tabCacheRegistry.activeCacheKeys() // 新增接口
    cacheRoot.listFiles()?.forEach { typeDir ->
        typeDir.listFiles()?.forEach { dir ->
            val cacheKey = dir.name
            if (cacheKey !in activeKeys && dir.isDirectory &&
                dir.listFiles()?.isEmpty() == false && dir.lastModified() < cutoff
            ) {
                dir.deleteRecursively()
            }
        }
    }
}
```

---

### 🟠 MEDIUM（9 条）

| # | 标题 | 位置 | 机制简述 |
|---|---|---|---|
| **I6** | 取消检测与协程模型不符 | [CacheOpenManager.kt](../core/cache/CacheOpenManager.kt#L88) | `Thread.currentThread().isInterrupted` 在协程取消时通常不被置位；应改用 `ensureActive()`。注：`emit()` 自身会检查取消，实际取消仍生效，故降级为 MEDIUM。 |
| **I7** | `FilesFragment` 超 1200 行 God Class | [FilesFragment.kt](../app/src/main/java/html_reader/FilesFragment.kt) | 本地/FTP/SMB 浏览、收藏、编码诊断、上传下载、增删改混于单类；`matchesQuery` 在 FTP 下逐条目尝试最多 6 种字符集解码（[L585-L604](../app/src/main/java/html_reader/FilesFragment.kt#L585-L604)）。 |
| **I8** | 硬编码 UI 字符串未本地化 | [FilesFragment.kt](../app/src/main/java/html_reader/FilesFragment.kt#L349) `"Open in new tab"`、[L364](../app/src/main/java/html_reader/FilesFragment.kt#L364) `"Diagnose Encoding"`、[L1203-L1204](../app/src/main/java/html_reader/FilesFragment.kt#L1203-L1204) `"Rename"/"New name"`、[L1227-L1229](../app/src/main/java/html_reader/FilesFragment.kt#L1227-L1229) `"Delete"/"Are you sure..."`；[MainActivity.kt](../app/src/main/java/html_reader/MainActivity.kt#L221) `"This file is already opened"`；[ReaderFragment.kt](../app/src/main/java/html_reader/ReaderFragment.kt#L259) `"Error Details"` 等 | 部分字符串与 `strings.xml` 值重复，直接写死。 |
| **I9** | 手工导航状态机脆弱 | [MainActivity.kt](../app/src/main/java/html_reader/MainActivity.kt#L240-L266) | 魔法 tag（`directory_mode_folders`/`reader_mode`/`more_overview` 等）+ `isProgrammaticSelection`/`isUserNavigation` 标志手工管理 hide/show 与返回栈。 |
| **I11** | 标题刷新 O(N²) 全量渲染 | [FilesFragment.kt](../app/src/main/java/html_reader/FilesFragment.kt#L1179-L1199) | `onResolvedTitle` 每解析一个标题就 `renderEntries()` 一次全量重绘。 |
| **I13** | WebView 本地文件访问面过大 | [WebViewConfigurator.kt](../core/reader/web/WebViewConfigurator.kt#L27-L28) `allowFileAccess=true, allowContentAccess=true` | 结合 [BlockingResourceWebViewClient.kt](../core/reader/web/BlockingResourceWebViewClient.kt#L18-L39) 白名单放行 `content://`，可被构造的 MHTML 探测本地文件；建议按需关闭并收紧。 |
| **I14** | PDF 位图不回收 | [AndroidPdfReaderController.kt](../core/reader/pdf/impl/AndroidPdfReaderController.kt#L45-L62) + [ReaderFragment.kt](../app/src/main/java/html_reader/ReaderFragment.kt#L443-L454) | 翻页 `setImageBitmap` 前旧 Bitmap 未 `recycle()`，大 PDF 连续翻页内存持续累积。 |
| **I15** | `CacheEvictor` 全树遍历开销 + 15GB 上限失效 | [CacheEvictor.kt](../core/cache/CacheEvictor.kt#L26-L43) + [ReaderRuntime.kt](../app/src/main/java/html_reader/ReaderRuntime.kt#L36-L38) | `makeRoomFor` 每次两遍 `walkTopDown` 全量扫描；`maxBytes=15GB` 远超 `cacheDir` 实际容量，淘汰逻辑几乎永不触发却仍付扫描开销。 |
| **I20** | 异常详情直接暴露并可复制 | [ReaderFragment.kt](../app/src/main/java/html_reader/ReaderFragment.kt#L254-L270) | `setErrorState` 把完整异常 message 展示到对话框并提供"Copy"（含文件路径、类名等），建议仅显示用户可理解的摘要，详细日志落 `Log.d`。 |

### 🟡 LOW（4 条）

| # | 标题 | 位置 | 机制简述 |
|---|---|---|---|
| **I16** | 死代码/重复实现 | [Migration1To2.kt](../core/database/migration/Migration1To2.kt#L9-L21)（从未注册）、重复的 [IFileSystem.kt](../core/vfs/IFileSystem.kt)（[core-storage 副本](../core-storage/src/main/java/core/vfs/IFileSystem.kt)）、`OpenFavoriteUseCase`（无调用）、[ReaderFragment.inferType](../app/src/main/java/html_reader/ReaderFragment.kt#L465-L472) / [FilesFragment.inferType](../app/src/main/java/html_reader/FilesFragment.kt#L1277-L1284) 中 `else -> FileType.MHTML` 使 html/htm 误判为 MHTML | 删除或接线。 |
| **I17** | 残留开发注释 | [DefaultReaderTabManager.kt](../core/reader/tab/DefaultReaderTabManager.kt#L110)（"需要你在 repo 增加该方法"）、[L138](../core/reader/tab/DefaultReaderTabManager.kt#L138)（"若你包1..."）、`LocalFileSystem.kt` L83-84（"见包1"）、`Migration1To2` 头注"仅占位" | 清除或落实。 |
| **I19** | 废弃 API 与过期 Manifest 配置 | [WebViewProgressTracker.kt](../core/reader/web/WebViewProgressTracker.kt#L26)（废弃的 `webView.scale`）、[AndroidManifest.xml](../app/src/main/AndroidManifest.xml#L5-L6)（`READ/WRITE_EXTERNAL_STORAGE`，targetSdk 36 已失效）、[L12](../app/src/main/AndroidManifest.xml#L12) `largeHeap`、[L17](../app/src/main/AndroidManifest.xml#L17) `usesCleartextTraffic` | 清理。 |
| **I21** | `darkMode` 空分支 | [WebViewConfigurator.kt](../core/reader/web/WebViewConfigurator.kt#L51-L53) | `if (darkMode) { Unit }` 为占位，未实现任何深色适配，且无人传参；建议删除参数或实现。 |

---

## 5. Phase 3：风险矩阵

| ID | 问题 | 维度 | 严重度 | 修复成本 |
|---|---|---|---|---|
| I1 | v3→v4 迁移缺失 → 升级清库 | 数据 | 🔴 Critical | 低（补一段 DDL + 注册） |
| I2 | 凭据明文存储/导出/备份 | 安全 | 🔴 Critical | 中（Keystore + 改造连接） |
| I3 | 六目录未编译，Hilt/维护体系失效 | 架构 | 🟠 High | 高（目录归位/迁移） |
| I4 | FTP/SMB 下载缓存永久泄漏 | 资源 | 🟠 High | 低 |
| I5 | tabStates 数据竞争 | 并发 | 🟠 High | 低 |
| I10 | 图片保存无上限 OOM | 安全 | 🟠 High | 低 |
| I12 | 维护任务双轨重复调度 | 架构 | 🟠 High | 低 |
| I18 | 误删活动 tab 缓存 | 正确性 | 🟠 High | 低 |
| I6 | 取消检测与协程模型不符 | 并发 | 🟡 Medium | 低 |
| I7 | FilesFragment God Class | 可维护 | 🟡 Medium | 高 |
| I8 | 硬编码字符串 | 可维护 | 🟡 Medium | 低 |
| I9 | 手工导航状态机 | 可维护 | 🟡 Medium | 高 |
| I11 | 标题刷新 O(N²) | 性能 | 🟡 Medium | 低 |
| I13 | WebView 本地文件访问面 | 安全 | 🟡 Medium | 低 |
| I14 | PDF 位图不回收 | 资源 | 🟡 Medium | 低 |
| I15 | CacheEvictor 全树扫描 + 上限失效 | 性能 | 🟡 Medium | 低 |
| I20 | 异常详情暴露 | 安全 | 🟡 Medium | 低 |
| I16 | 死代码/重复实现 | 可维护 | 🟢 Low | 中 |
| I17 | 残留开发注释 | 可维护 | 🟢 Low | 低 |
| I19 | 废弃 API/过期 Manifest | 可维护 | 🟢 Low | 低 |
| I21 | darkMode 空分支 | 可维护 | 🟢 Low | 低 |

---

## 6. Phase 4：改进路线图

### P0 — 立即修复（CRITICAL，数据与安全优先）
1. **I1**：补 `MIGRATION_3_4` 并在 `CoreRuntime` 注册；同时移除 `fallbackToDestructiveMigration()` 或仅在 DEBUG 构建启用。
2. **I2**：密码字段接入 Keystore + AES-GCM；`buildFtpUrl` 改显式凭据；备份导出字段加密；`allowBackup=false`。

### P1 — 近期迭代（HIGH，正确性与资源）
3. **I18**：`OrphanCacheCleaner` 接入 `TabCacheRegistry` 排除活跃 key；**I4**：FTP/SMB 下载纳入统一缓存治理。
4. **I5**：`tabStates` 加 `Mutex` + 不可变快照；**I6**：`ensureActive()` 替代线程中断检测。
5. **I10**：图片下载/解码加大小上限；**I14**：Bitmap `recycle()`。
6. **I12**：收敛双轨调度为单一来源；**I3**：孤儿目录并入编译（先低风险合并，再逐步物理迁移）。
7. **I7/I11**：先拆 `refreshTitlesAsync` 的 O(N²) 渲染，再逐步拆 God Class；**I9**：评估 Navigation 组件替换手工状态机。

### P2 — 架构演进（长期）
8. **I3 根治**：彻底废弃 `setSrcDirs` 软拆分，目录物理迁移进模块 `src/main/java`，建立清晰依赖方向（base ← data ← domain ← feature ← app）。
9. **DI 收敛**：二选一（Hilt 全线 or 手工 Runtime），消灭三套并存；`core/di` 若启用则接线。
10. **I8**：全量字符串资源化 + 文案审查；**I15**：缓存淘汰改按需增量统计 + 动态配额；**I13**：WebView 最小权限原则。

---

## 附录：CLAUDE.md 专项审计

审查日期：2026-08-16（同日完成修订并提交，见 commit `0ebce4c`）

### 结论摘要

CLAUDE.md 总体是**一份可执行的协作规范**，但与仓库实际状态存在**显著漂移**：4.4 节把不参与编译的六个目录列为活跃能力，遗漏真正编译的核心目录，也未记录工程最关键机制 `sourceSets.setSrcDirs` 软拆分。违反其自身 5.3 节"文档内容应与仓库当前状态一致"规则。

### 一致性核对

| CLAUDE.md 声明 | 实际状态 | 结论 |
|---|---|---|
| AGP 8.5.2 + Gradle 8.10 + JDK 17 | 根 build.gradle.kts / gradle-wrapper.properties | ✅ 一致 |
| compileSdk=36 / targetSdk=36 / minSdk=30 | app/build.gradle.kts | ✅ 一致 |
| 4.2 页面清单（9 个 Fragment/Activity） | Glob 全存在 | ✅ 一致（遗漏 Worker/Store 类） |
| 4.4 `core/network`、`core/work`、`core/settings`、`core/favorites` 为"能力目录" | **不参与编译**（孤儿目录） | ❌ 严重漂移 |
| 1.3 架构概览"多模块 + Runtime 装配" | 存在，但未提 setSrcDirs 软拆分 | ❌ 关键机制缺失 |
| 3.3.3 "当前可见 MIGRATION_2_3 与 destructive fallback" | 属实，但未警示数据风险 | ⚠️ 有隐患但未标注 |

### 修订落地内容（commit `0ebce4c`）

1. 新增 **1.4 源码组织与模块映射**：srcDirs 映射表 + 孤儿目录清单（`core/favorites`、`core/settings`、`core/network`、`core/work`、`core/maintenance`、`core/di`）+ 同包重复告警。
2. **1.2 补全版本基线**：Kotlin 1.9.24、Room 2.6.1、Hilt 2.52、Coroutines 1.8.1、serialization 1.6.3、jcifs-ng 2.1.10、versionName/versionCode。
3. **4.4 索引重写**，按编译归属标注 `[C→模块]` / `[O]`。
4. **2.2.6 新增数据安全约束**：实体/DAO 变更必须注册对应 Migration；凭据禁止明文写入日志、URL 或备份。
5. **3.3.3 强化迁移警示**：禁止默认依赖 destructive fallback。
6. **5/6/7 节修订**：commit message 格式规范；交付规则明确 versionCode 与 versionName 同步递增、仅功能性改动生成 APK。

### 附带提交

- `0d78762` `feat(cache,reader): version-stamp cache keys to avoid stale tab reuse`（上一会话遗留的功能改动，与审查 I5 相关，一并清理提交）
