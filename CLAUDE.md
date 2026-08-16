# CLAUDE 协作开发指南（mhtml_reader）

## 0. 协作硬性约束

1. 不要分析 `bundle_*.txt` 和 `apply_bundle.py`。  
2. 先理解需求与现有设计，再实施开发。  
3. 在得到用户明确允许之前，不要删除任何功能。  

---

## 1. 项目说明

### 1.1 应用用途

`mhtml_reader` 是一个 Android 本地文档阅读与文件管理应用，核心目标：

- 阅读：支持 `MHTML/MHT` 与 `PDF`。
- 管理：本地目录、SAF 目录、网络目录（SMB/FTP）浏览与打开。
- 体验：多标签阅读、阅读进度记录、收藏与历史、缓存与后台维护任务。

### 1.2 技术栈与关键版本（以当前代码为准）

- 平台：Android
- 语言：Kotlin `1.9.24`（`kotlin.code.style=official`）
- UI：View + XML（非 Compose）
- 构建：AGP `8.5.2` + Gradle `8.10`
- JDK 目标：Java `17`（`sourceCompatibility/targetCompatibility/jvmTarget=17`）
- SDK：
  - `compileSdk = 36`
  - `targetSdk = 36`
  - `minSdk = 30`
- 版本号：`versionName = 1.0.9` / `versionCode = 10`（见 `app/build.gradle.kts`）
- 核心依赖（关键版本）：
  - AndroidX（AppCompat `1.7.0`、Lifecycle `2.8.4`、WorkManager `2.9.1`、WebKit `1.11.0`、Room `2.6.1`）
  - Hilt `2.52`
  - Coroutines `1.8.1`
  - kotlinx-serialization `1.6.3`
  - jcifs-ng `2.1.10`（SMB）

### 1.3 架构概览

- 工程形态：Android 多模块（`app` + `core-*` + `feature-*`），模块注册见 `settings.gradle.kts`。
- 源码组织：**软拆分** —— 业务源码统一位于 `core/` 目录树，各模块通过 `sourceSets.main.java.setSrcDirs(...)` 将 `core/` 下子目录“虚拟映射”进模块参与编译（详见 1.4）。修改任何 `core/` 代码前，务必先确认目标目录归属哪个模块、是否参与编译。
- 运行方式：`app` 为 UI 壳与导航入口，主要业务实现位于 `core/` 目录。
- 当前依赖装配：存在 Runtime 装配对象（如 `CoreRuntime`、`FilesRuntime`、`ReaderRuntime`）进行依赖组织；`core/di`、`core/*/di` 下的 Hilt `@Module` 多数未参与编译接线，改动 DI 前先确认生效路径。

### 1.4 源码组织与模块映射（软拆分机制，强制阅读）

> 项目没有把 `core/` 源码物理复制进各模块，而是通过 `sourceSets.main.java.setSrcDirs(...)` 在构建期“虚拟并入”。**某目录是否参与编译，取决于是否被列在某个模块的 srcDirs 中。**

| 模块 | srcDirs 映射（`build.gradle.kts`） |
|---|---|
| `app` | `src/main/java` |
| `core-base` | `core/common`、`core/vfs/model` |
| `core-storage` | `src/main/java`、`core/vfs/impl`、`core/vfs/local` |
| `core-data` | `core/database`、`core/data/repo`、`core/session/dao`、`core/session/entity` |
| `core-domain` | `core/cache`、`core/domain`、`core/title`、`core/backup` |
| `feature-files` | `core/fileops`、`core/files`、`core/session/repo`、`core/session/di` |
| `feature-reader` | `core/reader` |

**孤儿目录（暂不参与编译）**：`core/favorites`、`core/settings`、`core/network`、`core/work`、`core/maintenance`、`core/di`。
⚠️ 在这些目录下新增/修改代码不会生效；其中的 Hilt Module、Worker、维护调度均为“已设计未接线”状态。若需启用，必须先加入某模块的 srcDirs（或物理迁移），并同步更新本文档。

**同包重复告警**：`core/vfs/IFileSystem.kt`、`core/FileSystemResolver.kt` 与 `core-storage/src/main/java/core/vfs/` 下的同名实现重复，前者未编译、后者生效。改动 VFS 抽象时以 `core-storage` 侧为准。

---

## 2. Coding Style 要求

### 2.1 通用规范

1. 遵循 Kotlin 官方风格，保持简洁、可读、可维护。  
2. 优先小函数与单一职责：普通函数实现体不超过 40 行；Fragment 内函数实现体不超过 60 行；超过阈值必须拆分。  
3. 命名语义化：
   - 类名/对象名：名词或名词短语
   - 函数名：动词或动宾短语
   - 常量：`UPPER_SNAKE_CASE`
4. 避免魔法值，提取为常量或集中配置。
5. 新增日志必须可检索且有上下文（模块名/场景/关键参数），避免噪音日志。

### 2.2 Kotlin/Android 实践

1. 空安全优先：先处理可空分支，减少 `!!`。  
2. 协程必须绑定生命周期（如 `viewLifecycleOwner.lifecycleScope`），禁止悬挂任务泄漏。  
3. I/O 与主线程职责明确：重操作在 `Dispatchers.IO`，UI 更新在主线程。  
4. Fragment 中注意状态一致性：
   - 先校验当前选中 tab/页面，再更新 UI 或提交副作用。
   - 处理配置切换与重复回调的幂等性。  
5. WebView/PDF 相关改动必须兼顾：
   - 安全边界（外链、脚本、资源访问）
   - 阅读体验（缩放/布局）
   - 进度记录一致性
6. 数据安全约束：
   - 数据库实体/DAO 变更必须同步编写并注册对应 `Migration`（当前版本号 `4`，已注册 `1→2`、`2→3`、`3→4` 迁移），禁止依赖 `fallbackToDestructiveMigration()` 兜底（升级会清空用户数据）。
   - 网络凭据（SMB/FTP 密码）禁止明文写入日志、URL 字符串或备份导出文件。

### 2.3 提交与重构要求

1. 不做无关重构；每次改动聚焦当前需求。  
2. 若调整公共能力（如 `core/*`），需要同步检查引用方（`app` 页面与 Runtime 装配）。  
3. 影响行为的改动，至少补充：
   - 关键路径手工验证步骤
   - 必要日志或测试说明

---

## 3. 开发环境说明

### 3.1 本地环境基线

- Android Studio（近期稳定版，支持 AGP 8.5+）
- JDK 17
- Android SDK Platform 36 + Build-Tools 36.1.0
- Gradle 使用 Wrapper（`gradle-8.10-bin.zip`）
- JDK：项目以 **JDK 17** 为构建基线（`sourceCompatibility/targetCompatibility/jvmTarget=17`）。机器上同时装有 JDK 17 与 JDK 21，构建时请显式指定 `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`（AGP 8.5.2 不支持在 JDK 21 下编译资源，`mergeDebugResources` 会报 `ParsedResource` 提取失败）。

### 3.2 常用命令（Windows / 项目根目录）

```bash
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug
.\gradlew.bat test
.\gradlew.bat connectedAndroidTest
```

### 3.2.1 测试基础设施（2026-08-16 补齐）

- `core-base/src/test/java`：纯 JVM 单元测试（`HashUtilsTest` / `OutcomeTest` / `AppErrorTest`），不依赖模拟器。
- `app/src/test/java`：纯 JVM 单元测试（`FilesSortHelperTest`），不依赖模拟器。
- `app/src/androidTest/java`：设备端冒烟测试（`AppSmokeTest`），验证 app 冷启动不崩，需连接模拟器/真机。
- 本地运行：`./gradlew :core-base:testDebugUnitTest`、`:app:testDebugUnitTest`、`:app:connectedDebugAndroidTest`。

### 3.3 构建与运行注意事项

1. 本项目已在 `gradle.properties` 中配置部分 kapt 稳定性参数，避免随意回退。  
2. Manifest 涉及存储与网络权限，调试文件系统能力前先确认设备授权状态。  
3. 若改动数据库实体/DAO，需同步评估迁移策略：必须在 `AppDatabase.companion` 中注册对应版本迁移（当前已注册 `Migration1To2`、`MIGRATION_2_3`、`MIGRATION_3_4`；`fallbackToDestructiveMigration()` 仅在 DEBUG 构建启用），禁止把 `fallbackToDestructiveMigration()` 当作默认兜底（升级将清空用户数据）。

---

## 4. 项目文件结构（快速定位）

> 目标：减少无效目录扫描，优先定位“入口文件 + 能力目录”。  
> **强制要求：后续重构代码时，必须同步更新本节内容。**

### 4.1 根目录关键项

- `app/`：应用入口、页面 UI、导航与运行时装配
- `core/`：核心业务实现（阅读、文件、数据、缓存、网络、任务）
- `core-base/` `core-data/` `core-domain/` `core-storage/`：模块配置与基础能力
- `feature-files/` `feature-reader/`：功能模块配置
- `settings.gradle.kts`：模块注册入口
- `app/build.gradle.kts`：应用构建配置与 SDK 版本

### 4.2 App 页面快速定位

- `app/src/main/java/html_reader/MainActivity.kt`：主导航与页面切换
- `app/src/main/java/html_reader/HomeFragment.kt`：首页入口
- `app/src/main/java/html_reader/FilesFragment.kt`：文件浏览/操作页
- `app/src/main/java/html_reader/ReaderFragment.kt`：阅读页（WebView + PDF）
- `app/src/main/java/html_reader/FavoritesFragment.kt`：收藏页
- `app/src/main/java/html_reader/RecentsFragment.kt`：历史页
- `app/src/main/java/html_reader/MoreFragment.kt`：更多/设置页
- `app/src/main/java/html_reader/TabsOverviewFragment.kt`：阅读标签总览
- `app/src/main/java/html_reader/FoldersOverviewFragment.kt`：目录会话总览

### 4.3 Runtime 与核心入口

- `app/src/main/java/html_reader/CoreRuntime.kt`：数据库与基础调度初始化
- `app/src/main/java/html_reader/FilesRuntime.kt`：文件域依赖装配
- `app/src/main/java/html_reader/ReaderRuntime.kt`：阅读域依赖装配

### 4.4 Core 能力目录索引（按编译归属标注）

> `[C → 模块]` = 参与该模块编译；`[O]` = 孤儿目录（暂不编译，见 1.4）。修改前先确认归属。

- `core/common` `[C → core-base]`：DispatcherProvider、AppError、HashUtils 等公共基础
- `core/vfs/model` `[C → core-base]`：VfsPath、VfsEntry 模型
- `core/vfs/impl`、`core/vfs/local` `[C → core-storage]`：虚拟文件系统抽象与本地实现
- `core/database` `[C → core-data]`：Room 数据库、DAO、实体、迁移（`Migration1To2`、`MIGRATION_2_3`、`MIGRATION_3_4` 均已注册）
- `core/data/repo` `[C → core-data]`：仓储实现（收藏/历史/网络配置/标题缓存）
- `core/session/*` `[C → core-data / feature-files]`：目录会话实体、DAO、仓储与用例
- `core/cache` `[C → core-domain]`：缓存打开、淘汰与清理
- `core/domain` `[C → core-domain]`：领域模型与用例（目录列表/标题/历史保留）
- `core/title` `[C → core-domain]`：标题提取能力（HTML/PDF）
- `core/backup` `[C → core-domain]`：JSON 导入导出
- `core/fileops` `[C → feature-files]`：复制/移动/删除/重命名/建目录
- `core/files` `[C → feature-files]`：目录会话用例
- `core/reader` `[C → feature-reader]`：阅读器模型、标签、PDF/Web 适配、ViewModel
- `core/favorites` `[O]`：收藏树模型与用例（未编译）
- `core/settings` `[O]`：应用设置与 DataStore（未编译）
- `core/network` `[O]`：网络配置用例与连接测试（未编译）
- `core/work` `[O]`：后台任务调度与 Worker（未编译；实际生效的 Worker 位于 `app` 包内）
- `core/maintenance` `[O]`：维护管理器（未编译）
- `core/di` `[O]`：核心 DI 装配（未编译）

### 4.5 Web 阅读相关（高频）

- `core/reader/web/WebViewConfigurator.kt`：WebView 渲染配置
- `core/reader/web/BlockingResourceWebViewClient.kt`：外链/资源拦截策略
- `core/reader/web/WebViewProgressTracker.kt`：阅读进度跟踪

---

## 5. 文档维护规则

1. 变更以下任一内容时，必须同步更新本文件：
   - 模块结构、目录职责、入口文件路径
   - 模块 srcDirs 映射或编译归属（1.4、4.4）
   - SDK/JDK/AGP/Gradle 版本基线
   - 开发约束与协作规范
2. 若新增跨模块能力，先补“目录索引与定位说明”，再提交代码。  
3. 文档内容应与仓库当前状态一致，禁止保留过期说明。  
4. 目录索引（4.4）必须标注编译归属 `[C]/[O]`，与 1.4 的映射表保持一致。

---

## 6. git

1. 每次对话结束之后，如果对代码有修改，必须做 git commit 并附上本次修改 message。
2. commit message 使用简洁、可预测的格式：`<type>: <简述>`（如 `fix: 补 3→4 数据库迁移`、`docs: 更新模块映射`）。

---

## 7. 交付

1. 仅在功能性代码修改后生成 APK：每次递增 `versionName`（+0.0.1）并同步递增 `versionCode`（+1）。纯文档/注释类修改不生成 APK，但仍须按第 6 节提交。
2. 生成的 APK 命名遵循 `MHTMLReader_v<versionName>_<buildType>.apk`（`app/build.gradle.kts` 已配置输出规则）。