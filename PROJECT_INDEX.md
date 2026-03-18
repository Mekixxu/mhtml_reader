# 工程索引（mhtml_reader）

## 1. 工程概览

- 类型：Android 多模块项目（Kotlin + View/XML）
- 根模块：`app`（应用壳）+ `core-*` / `feature-*`（通过 sourceSets 映射 `core/` 子目录）
- 主要能力：文件浏览与操作、MHTML/PDF 阅读、多标签、收藏与历史、网络位置（SMB/FTP）、缓存与维护任务

## 2. 根目录索引

- `app/`：应用入口、主导航、页面 UI 与运行时装配
- `core/`：核心业务实现（数据、领域、存储、阅读器、文件系统、任务调度等）
- `core-base/`：基础核心模块 Gradle 配置
- `core-data/`：数据核心模块 Gradle 配置
- `core-domain/`：领域核心模块 Gradle 配置
- `core-storage/`：存储核心模块（含 vfs 接口镜像）
- `feature-files/`：文件功能模块 Gradle 配置
- `feature-reader/`：阅读器功能模块 Gradle 配置
- `gradle/`：Gradle Wrapper 与构建支持文件
- `.trae/specs/`：历史规格与任务清单

## 3. 启动与主链路

- 应用入口：`app/src/main/java/html_reader/HtmlReaderApp.kt`
- 主页面入口：`app/src/main/java/html_reader/MainActivity.kt`
- 主导航布局：`app/src/main/res/layout/activity_main.xml`
- 底部导航菜单：`app/src/main/res/menu/main_bottom_nav_menu.xml`

## 4. app 模块页面索引

- `HomeFragment.kt`：主页入口
- `FilesFragment.kt`：文件浏览与文件操作主页面
- `ReaderFragment.kt`：阅读器页面（WebView/PDF 协同）
- `FavoritesFragment.kt`：收藏页面
- `RecentsFragment.kt`：历史页面
- `MoreFragment.kt`：设置、网络配置、导入导出等
- `TabsOverviewFragment.kt`：阅读标签总览
- `FoldersOverviewFragment.kt`：目录会话总览

## 5. 运行时装配索引

- `CoreRuntime.kt`：核心依赖装配入口
- `FilesRuntime.kt`：文件域依赖装配
- `ReaderRuntime.kt`：阅读域依赖装配

## 6. core 核心目录索引

- `core/common/`：通用模型与结果封装（错误、Outcome、调度器等）
- `core/database/`：Room 数据库、DAO、实体、迁移与模块注入
- `core/data/repo/`：收藏/历史/网络配置/标题缓存仓库
- `core/domain/`：展示模型与领域用例
- `core/files/`：目录会话、目录观察、扩展名策略
- `core/fileops/`：复制/移动/删除/重命名/建目录等操作用例
- `core/reader/`：阅读器标签管理、进度保存、PDF/Web 适配
- `core/title/`：HTML/PDF 标题提取与路由
- `core/cache/`：缓存打开、淘汰、标签缓存登记、孤儿清理
- `core/network/`：网络配置模型、校验、连接测试用例
- `core/favorites/`：收藏树模型与操作用例
- `core/settings/`：应用设置模型与 DataStore
- `core/session/`：目录会话持久化
- `core/vfs/`：虚拟文件系统抽象与本地实现
- `core/work/`：维护任务 worker 与调度器
- `core/backup/`：导入导出模型与 JSON 备份管理
- `core/maintenance/`：维护任务统一管理

## 7. 关键文件快速索引

- 构建与模块注册：
  - `settings.gradle.kts`
  - `build.gradle.kts`
  - `app/build.gradle.kts`
- 数据库核心：
  - `core/database/AppDatabase.kt`
  - `core/database/dao/`
  - `core/database/entity/`
- 文件系统与解析：
  - `core/vfs/IFileSystem.kt`
  - `core/vfs/impl/DefaultFileSystemResolver.kt`
  - `core/vfs/local/LocalFileSystem.kt`
- 阅读能力：
  - `core/reader/tab/DefaultReaderTabManager.kt`
  - `core/reader/vm/ReaderViewModel.kt`
  - `app/src/main/java/html_reader/ReaderFragment.kt`
- 文件操作：
  - `core/fileops/usecase/ExecuteFileOpUseCase.kt`
  - `app/src/main/java/html_reader/FilesFragment.kt`
- 维护与缓存：
  - `core/cache/CacheOpenManager.kt`
  - `app/src/main/java/html_reader/AppOrphanCacheCleanupWorker.kt`
  - `app/src/main/java/html_reader/AppHistoryRetentionWorker.kt`

## 8. 建议使用方式

- 查找页面入口：优先从 `app/src/main/java/html_reader/` 对应 Fragment 入手
- 查找业务逻辑：按能力进入 `core/<能力名>/` 查看 `domain/usecase` 与 `repo`
- 查找数据落库：从 `core/database/` 的实体与 DAO 反向追踪到 repository
- 查找后台任务：从 `HtmlReaderApp.kt` 与 `core/work/` 的调度和 worker 入手
