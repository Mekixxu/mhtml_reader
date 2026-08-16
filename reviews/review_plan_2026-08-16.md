# mhtml_reader 升级执行计划（Review Plan）

> - 生成日期：2026-08-16
> - 基线：`463a117`，`versionName = 1.0.14` / `versionCode = 15`
> - 上游报告：`reviews/claude_sonnet4_2026-08-16.md`（21 条问题清单）
> - 用途：交给下一个 LLM / 开发者继续完成剩余升级
> - 原则：每批功能修改独立 commit，并同步递增版本号与更新 `CLAUDE.md` 版本基线；不删除任何现有功能

---

## 0. 工作环境（已经验证可编译）

| 项 | 值 |
|---|---|
| Android SDK | `/opt/android-sdk`（`local.properties` 已存在） |
| JDK | `/usr/lib/jvm/java-17-openjdk-amd64`（必须用 JDK 17，JDK 21 会触发 `mergeDebugResources` 失败） |
| Gradle | `/home/alioth/gradle-8.10/gradle-8.10/bin/gradle`（Gradle 8.10；系统自带 Gradle 9.7 与 AGP 8.5.2 不兼容，勿用） |
| 构建命令 | `export ANDROID_HOME=/opt/android-sdk && export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 && /home/alioth/gradle-8.10/gradle-8.10/bin/gradle :app:assembleDebug` |
| 单元测试 | 追加 `:core-base:testDebugUnitTest :app:testDebugUnitTest` |
| 冒烟测试 | `:app:connectedDebugAndroidTest`（需要模拟器/真机） |

工作流要求：

1. 每完成一个任务，运行 `:app:assembleDebug` 与相关单元测试，确认 `BUILD SUCCESSFUL` 后再 commit。
2. 每个 commit 使用 `<type>: <简述>`（如 `fix:`、`refactor:`、`perf:`、`test:`、`docs:`）。
3. 功能代码修改：`versionName +0.0.1`，`versionCode +1`，并同步更新 `CLAUDE.md` 1.2 节版本行。
4. 不要修改 `bundle_*.txt`、`apply_bundle.py`；不要删除功能，除非用户明确允许。
5. 若修改数据库实体/DAO，必须同步新增并注册 Migration；Release 路径禁止 `fallbackToDestructiveMigration()`。

---

## 1. 已完成的修复（请勿重复实施）

| 问题 | 完成内容 | Commit |
|---|---|---|
| H1 / SQL | `HistoryDao.deleteOldest` 负数 LIMIT 清空全表 | `7e667e8` |
| H3 / I1 | 注册 `Migration1To2`、`MIGRATION_2_3`、`MIGRATION_3_4`，Release 不用 destructive fallback | `7e667e8` |
| I2 部分 | `allowBackup=false`、密码输入掩码、JSON 导出不再写密码 | `7e667e8` |
| I4 | `TransferCacheCleaner` 清理 `ftp_open`/`smb_open` 遗留中转文件 | `825a76d` |
| I5 | `DefaultReaderTabManager.tabStates` 加 synchronized 访问 + 插入前二次去重 | `55ea486` |
| I6 | `LocalFileSystem`/`ExecuteFileOpUseCase` 取消异常重抛；`CacheOpenManager` 用 `coroutineContext.ensureActive()` | `7e667e8`、`cbe1ea7` |
| I7 部分 | `displayTitleByPath`/`ftpDecodeCache` 改 `ConcurrentHashMap`；标题刷新每批只 render 一次 | `d8c200c`、`7b113ca` |
| I8 部分 | `FilesFragment`/`ReaderFragment`/`FilesStatusUiHelper`/`FilesEntryDetailsBuilder` 主要硬编码串资源化 | `49bdcce`、`38ee9f9` |
| I10 | 图片保存 20MB 上限，HTTP 流式读限长，data URL 预估/检查大小 | `3402f3d` |
| I11 | 标题刷新由每个标题触发 `renderEntries()` 改为每批一次 | `7b113ca` |
| I13 | `allowContentAccess=false`，白名单移除 `content://` | `7b113ca`、`4959b74` |
| I14 | PDF 翻页旧 Bitmap `recycle()` | `7b113ca` |
| I15 | `CacheEvictor` 单次遍历计算目录大小；上限 2GB | `45d25e3`、`7b113ca` |
| I18 | `OrphanCacheCleaner.clean(activeCacheKeys)` 排除活跃 tab 缓存 | `7e667e8` |
| I19 部分 | Manifest 移除 `READ/WRITE_EXTERNAL_STORAGE` | `235d997` |
| I21 | `WebViewConfigurator` 移除空 `darkMode` 参数 | `825a76d` |
| I17 | 清理 VFS/TabManager/Migration 中的占位注释 | `825a76d` |
| 测试 | core-base 单测、FilesSortHelper/FilesFtpCodec/TransferCacheCleaner 单测 | `cbe1ea7`、`463a117` |
| L1 | `FilesNetworkOpenResolver` 的 `URLDecoder.decode` 包 `runCatching` | `7e667e8` |
| M4 | `ReaderFragment.onDestroyView` 中 PDF 关闭任务改独立 IO scope | `7e667e8` |
| M6 | `FilesFragment.loadRemoteEntries` 取消旧远程加载并重抛取消异常 | `7e667e8` |
| M7 | `supportedExtensions` 对齐 spec 为 `mht/mhtml/pdf` | `7e667e8` |

---

## 2. 剩余任务总览

| 优先级 | 任务 | 维度 | 复杂度 | 建议顺序 |
|---|---|---|---|---|
| P0 | T1 网络凭据 Keystore 加密存储 | 安全 | 高 | 1 |
| P1 | T2 孤儿目录接线或清除 | 架构 | 中 | 2 |
| P1 | T3 维护调度单源化 | 架构 | 低 | 3 |
| P1 | T4 错误详情脱敏 | 安全 | 低 | 4 |
| P1 | T5 `FilesFragment` 继续拆分 | 可维护 | 高 | 5 |
| P1 | T6 导航状态机改造 | 生命周期 | 高 | 6 |
| P2 | T7 死代码/重复实现治理 | 可维护 | 中 | 7 |
| P2 | T8 过期 API 与 Manifest 收尾 | 可维护 | 低 | 8 |
| P2 | T9 测试覆盖补强 | 测试 | 中 | 9 |

---

## 3. 任务详细说明

### T1. 网络凭据 Keystore 加密存储（P0，安全）

- **现状**：`NetworkConfigEntity.password` 明文存 Room；`FilesNetworkGateway.buildFtpUrl` 将凭据拼入 URL；`JsonBackupManager` 导出已不写密码，但 DB 仍是明文。
- **目标**：数据库中仅存密文；旧明文数据可读；新写入自动加密；读取自动解密。
- **建议方案**：
  1. 新增 `core/security/CredentialCipher.kt`，使用 `AndroidKeyStore` + `AES/GCM/NoPadding`。
  2. 密钥别名固定（如 `mhtml_reader_credential_key`），密钥不可导出。
  3. 密文格式：`enc:v1:<base64(iv)>:<base64(ciphertext)>`；解密时若前缀不是 `enc:v1:` 则视为旧明文直接返回，实现平滑迁移。
  4. 在 `NetworkConfigRepository` 的 `add/update` 入口加密 `password`，在读取路径（`getAll/getById`）解密；`dao` 保持 TEXT 列不变。
  5. 新增迁移？不需要，列类型不变；旧数据无前缀继续可读。
  6. `FilesNetworkGateway.buildFtpUrl` 暂时仍需凭据拼 URL；请在函数上加注释说明风险，并确保不会写入日志。长期可换 Apache Commons Net 或自定义 FTP client。
- **验收**：
  - 新添加/更新网络配置后，查 DB 或 `getAll()` 返回时密码正确但 DB 内为 `enc:v1:` 密文。
  - 旧明文配置仍能打开网络目录。
  - `:app:assembleDebug` 通过。
- **文件**：
  - 新增 `core/security/CredentialCipher.kt`（需要挂到某个已编译模块，建议 `core-data` 的 `srcDirs` 增加 `../core/security`，或直接放 `core/data/repo/` 内）。
  - `core/data/repo/NetworkConfigRepository.kt`
  - 可选：`app/src/main/java/html_reader/MoreFragment.kt`、`HomeFragment.kt` 无需改（repo 已屏蔽）

### T2. 孤儿目录接线或清除（P1，架构）

- **现状**：`core/favorites`、`core/settings`、`core/network`、`core/work`、`core/maintenance`、`core/di` 不在任何模块 `srcDirs`，未编译；其中 `core/favorites/domain/validator/FavoritesTreeValidator.kt` 引用了不存在的 `AppError.Conflict`，一旦接线会编译失败。
- **目标**：二选一：
  - **A. 接线**（推荐，符合原架构意图）：把这些目录挂入合适的 Gradle 模块，补齐依赖与 `AppError.Conflict`，让全部代码可编译、可被未来使用。
  - **B. 清除**：需要用户明确允许删除功能后才能执行。
- **建议接线映射**：
  - `core/di` → `core-domain`（CoreModule 提供 DispatcherProvider/LocalFileSystem/CacheOpenManager；需确认模块依赖）
  - `core/favorites` → `feature-files` 或 `app`
  - `core/network` → `core-domain` 或 `app`
  - `core/settings` → `core-domain`（需加 DataStore 依赖 `androidx.datastore:datastore-preferences:1.1.1`）
  - `core/work`、`core/maintenance` → `app` 或 `feature-files`（需加 WorkManager/Hilt Worker 依赖）
- **前置修复**：先在 `core/common/AppError.kt` 增加 `object Conflict : AppError("Conflict")`。
- **验收**：`./gradlew :app:assembleDebug` 通过；`grep -R "core/(favorites|settings|network|work|maintenance|di)"` 无未编译死目录告警；`CLAUDE.md` 1.4/4.4 映射表同步更新。

### T3. 维护调度单源化（P1，架构）

- **现状**：`HtmlReaderApp` 硬编码 `maxItems=500/maxDays=365/daysUnused=3`，并同时执行启动即清理与每日 Worker；`core/work` 与 `core/maintenance` 未接线，形成双轨。
- **目标**：保留一条生效路径。
- **建议**：
  1. 若 T2 选择接线，则启用 `DefaultWorkScheduler`/`MaintenanceWorker`，并删除 `HtmlReaderApp.scheduleMaintenanceWorkers()` 中重复逻辑。
  2. 若 T2 暂不接线，则在 `AppHistoryRetentionWorker`/`AppOrphanCacheCleanupWorker` 中集中参数读取，并在 `HtmlReaderApp` 注释说明唯一调度入口。
- **验收**：`HtmlReaderApp` 中不存在硬编码维护参数与明显重复调度；每日 Worker 仍正常注册。

### T4. 错误详情脱敏（P1，安全）

- **现状**：`ReaderFragment.setErrorState` 在对话框展示完整异常 message，并可复制。
- **目标**：用户看到友好摘要；完整详情仅写日志。
- **建议**：
  1. 在 `ReaderFragment.runOpen` 的 `OpenState.Error` 分支，将 `state.error.message` 写入 `Log.w("ReaderFragment", ...)`。
  2. `setErrorState(displayMsg, fullDetails)` 改为 `setErrorState(displayMsg)`，对话框只显示 `displayMsg`，复制也复制 `displayMsg`。
  3. `strings.xml` 增加通用错误标题/摘要（已有 `reader_status_error`，可复用）。
- **验收**：错误弹窗不再出现完整路径/异常栈；日志中可查到完整详情。

### T5. `FilesFragment` 继续拆分（P1，可维护）

- **现状**：`FilesFragment` 仍有 1200+ 行，集中了本地/FTP/SMB 浏览、文件操作、收藏、上传下载、编码诊断。
- **目标**：Fragment 只负责 UI 绑定与事件转发；业务状态进 ViewModel 或至少拆分控制器。
- **建议顺序**：
  1. 先抽 `FilesRemoteController`：`ftpConfig`/`smbConfig`/`ftpCurrentPath`/`smbCurrentPath`/`ftpResolvedCharset`/`ftpLoadToken`/`remoteLoadJob` 及 `loadFtpEntries`/`loadSmbEntries`/`loadRemoteEntries`/`fetchFtpEntries`/`openRemoteFile`/`uploadRemoteDocument`。
  2. 再抽 `FilesLocalController`：`currentDir`、`allEntries`、`displayedEntries`、`renderEntries`、`loadEntries`（本地分支）。
  3. 保留 `FilesFragment` 作为薄壳，只持有 View 引用和点击回调。
  4. 若条件允许，改用 `ViewModel` + `StateFlow`。
- **验收**：`FilesFragment` 行数明显下降（目标 < 700 行）；行为不回归；`:app:assembleDebug` 通过。

### T6. 导航状态机改造（P1，生命周期）

- **现状**：`MainActivity.switchFragment` 用 hide/show + 魔法 tag 管理页面，隐藏 Fragment 常驻，Flow 收集器不停止。
- **目标**：单一容器，隐藏页面生命周期可预期。
- **建议**：
  1. 短期：将隐藏页面的 Flow 收集统一绑定 `viewLifecycleOwner.lifecycleScope`，并在 `onHiddenChanged`/`onPause` 中做取消判断。
  2. 长期：接入 `Navigation Component` 或改为 `replace + addToBackStack`。
- **验收**：进入 Home 后，隐藏的 Files/Tabs/More 页面不再常驻 RESUMED；`onBackPressed` 行为与当前一致。

### T7. 死代码/重复实现治理（P2，可维护）

- **现状**：`core/vfs/IFileSystem.kt`、`core/vfs/FileSystemResolver.kt` 与 `core-storage/src/main/java/core/vfs/` 重复；`Migration1To2` 原本占位现已接线；`OpenFavoriteUseCase` 等无调用。
- **目标**：在不删除功能的前提下消除误导。
- **建议**：
  1. 在 `CLAUDE.md` 1.4 已标注重复；可把根目录重复文件移动到 `reviews/deprecated/` 或添加 `@Deprecated` 说明。
  2. 对无调用且明确无用的类，标注 `// Deprecated: 未接线`，待用户批准后删除。
- **验收**：源码树中没有「同名同包但一个不编译」的 VFS 接口；未接线代码有清晰标注。

### T8. 过期 API 与 Manifest 收尾（P2，可维护）

- **现状**：`WebViewProgressTracker` 使用废弃的 `WebView.scale`；`android:largeHeap=true`；`usesCleartextTraffic=true`（FTP 需要，需评估 network security config 只允许 FTP 明文）。
- **建议**：
  1. `WebViewProgressTracker`：先加 `@Suppress("DEPRECATION")` 并注释说明；若后续 WebView 提供替代 API 再迁移。
  2. `usesCleartextTraffic`：改用 `networkSecurityConfig`，仅对 FTP 相关域名/源允许明文。
  3. `largeHeap`：评估 PDF/WebView 内存占用，如非必要移除。
- **验收**：构建无新增 deprecation 警告（scale 除外，已抑制）；Manifest 安全配置更精细。

### T9. 测试覆盖补强（P2，测试）

- **目标**：
  - `HistoryDao.deleteOldest` 用 Room in-memory 测试验证「未超限删 0 条、超限删最旧」。
  - `DefaultReaderTabManager` 补一个同路径/同版本去重的 JVM 测试。
  - `CacheEvictor` 补淘汰顺序单测。
  - `FilesNetworkGateway` 路径拼接单测。
- **依赖**：`app` 或 `core-data` 的 test 需补 `androidx.room:room-testing`、`org.robolectric`（如需要）。
- **验收**：`./gradlew :core-base:testDebugUnitTest :app:testDebugUnitTest` 全绿；新增用例覆盖上述路径。

---

## 4. 验证清单（每个任务完成后执行）

```bash
export ANDROID_HOME=/opt/android-sdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
GRADLE=/home/alioth/gradle-8.10/gradle-8.10/bin/gradle

$GRADLE :app:assembleDebug
$GRADLE :core-base:testDebugUnitTest :app:testDebugUnitTest
git diff --check
```

---

## 5. Commit / 版本规则（遵循 CLAUDE.md）

- 功能修改：`versionName +0.0.1`、`versionCode +1`，并同步 `CLAUDE.md` 1.2 节版本行。
- 纯文档/注释/测试修改：可不递增版本，但仍需 commit。
- commit message 示例：
  - `fix(security): encrypt network credentials with Android Keystore`
  - `refactor(files): extract remote controller from FilesFragment`
  - `test(dao): cover history retention edge cases`
