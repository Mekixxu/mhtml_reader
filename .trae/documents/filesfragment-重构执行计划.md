# FilesFragment 重构执行计划（零行为变更）

## 1. Summary

- 目标文件：`app/src/main/java/html_reader/FilesFragment.kt`（当前约 1929 行）。
- 总目标：在**不改变业务行为**的前提下，完成结构化拆分，提升可读性、降低维护成本，并修复已识别的性能/风险点。
- 关键约束：
  - 允许拆分为多个新文件/类。
  - 严格零行为变更（UI 交互流程、功能范围、核心提示语保持一致）。
  - 验收同时覆盖：可读性 + 性能 + 风险修复。

## 2. Current State Analysis

### 2.1 结构问题

- `FilesFragment` 同时承担 UI 层、状态协调层、协议访问层（LOCAL/FTP/SMB）、标题提取刷新层。
- `onViewCreated` 过长，初始化、监听绑定、会话恢复、权限处理耦合在一个入口函数中。
- 多源加载逻辑分散（`loadEntries` / `loadFtpEntries` / `loadSmbEntries`），存在大量重复流程（按钮状态、状态文案、列表清空、错误处理）。

### 2.2 性能与风险问题（当前已定位）

- 列表渲染 `getView` 中逐项 `Log.d`，在大目录滚动时会放大主线程开销。
- 本地目录读取 `File.listFiles()` 与列表映射在主线程执行，目录大时容易卡顿。
- FTP/SMB 下载缓存文件使用“展示名”直接命名，重名覆盖风险较高。
- 收藏 FTP 条目时将用户名/密码拼入 URL，存在敏感信息落库存储风险。

## 3. Proposed Changes

> 说明：以下为“先重构骨架、再迁移实现、最后做等价验证”的顺序，确保每步可回归、可回滚。

### 3.1 新增文件与职责拆分

1. `app/src/main/java/html_reader/files/FilesUiBinder.kt`  
   - **What**：封装 View 绑定与 Adapter 展示逻辑（不含业务状态变更）。  
   - **Why**：减少 `FilesFragment` 中 UI 细节噪声。  
   - **How**：暴露 `bindViews(view)`、`createAdapter(...)`、`updateList(...)` 等接口，保持原布局与 item 展示文案不变。

2. `app/src/main/java/html_reader/files/FilesBrowseCoordinator.kt`  
   - **What**：统一 LOCAL/FTP/SMB 的浏览状态与路径切换。  
   - **Why**：避免 `browseSource/currentDir/ftpCurrentPath/smbCurrentPath` 在 Fragment 中散写。  
   - **How**：收敛为单一状态对象与统一入口（加载、进入目录、返回上级、持久化当前路径）。

3. `app/src/main/java/html_reader/files/FilesNetworkGateway.kt`  
   - **What**：承接 FTP/SMB 列表、下载、上传、重命名、删除、URL 编码与鉴权上下文。  
   - **Why**：将协议细节从 Fragment 解耦，便于测试与复用。  
   - **How**：抽取当前 `fetchFtpEntries/fetchSmbEntries/download*/upload*/build*Url/smbContext` 等方法，接口保持等价。

4. `app/src/main/java/html_reader/files/FilesTitleRefresher.kt`  
   - **What**：封装本地标题缓存读取与异步刷新流程。  
   - **Why**：降低 Fragment 对标题提取与缓存细节的耦合。  
   - **How**：保留现有 `title gate` 规则与缓存命中策略，输出增量 title map 回调。

5. `app/src/main/java/html_reader/files/FilesModels.kt`  
   - **What**：迁移 `BrowseSource`、`BrowserEntry`、`FtpRawEntry` 及纯模型工具函数。  
   - **Why**：减少 Fragment 内部类型噪声，提升可读性。  
   - **How**：数据结构字段保持一致，equals/hashCode 行为保持一致。

### 3.2 修改现有文件

1. `app/src/main/java/html_reader/FilesFragment.kt`  
   - **What**：重构为“页面编排器”（UI 事件路由 + 业务调用 + 生命周期管理）。  
   - **Why**：将复杂实现外移后，降低函数长度与变更冲击面。  
   - **How**：
     - 拆分 `onViewCreated` 为：`bindUi()`、`initDependencies()`、`setupControls()`、`setupListInteractions()`、`restoreSessionAndLoad()`。
     - 保持原有按钮行为、菜单行为、提示文案、导航入口不变。
     - 将网络与标题刷新调用切换到新类，不改变调用时机。

2. `app/src/main/java/html_reader/FilesRuntime.kt`（仅当需要）  
   - **What**：如新类需要依赖注入入口，则补充创建逻辑。  
   - **Why**：避免在 Fragment 中重复装配复杂依赖。  
   - **How**：仅新增装配，不改现有实例语义。

### 3.3 已识别风险的处理策略（零行为下）

1. 日志开销  
   - 将逐项 `display_entry` 日志降为可控条件日志（debug flag 或采样），避免滚动噪声。

2. 本地目录主线程阻塞  
   - 本地目录扫描迁移到 `Dispatchers.IO`，主线程只做结果应用（条目列表、状态标签刷新）。

3. 下载缓存重名覆盖  
   - 缓存文件命名引入稳定去重后缀（如 hash/时间戳），避免不同路径同名覆盖。

4. FTP 收藏敏感信息  
   - 收藏持久化改为“凭据脱敏路径”策略（不落库存明文密码）；运行时仍按原配置访问。

## 4. Assumptions & Decisions

- 决策已锁定：
  - 允许拆分新文件/类。
  - 严格零行为变更。
  - 验收同时覆盖可读性、性能、风险。
- 假设：
  - UI 文案与资源键值不调整（除非为修复编译错误的最小改动）。
  - 网络协议行为与路径规则保持与当前一致。
  - 不新增业务功能，不删除现有功能入口。

## 5. Verification Steps

### 5.1 静态与编译验证

1. 运行 `GetDiagnostics`，确保新增/修改文件无语法或类型错误。  
2. 构建验证：`.\gradlew.bat :app:assembleDebug` 成功。  
3. 检查 `FilesFragment` 关键函数长度满足协作规范（普通函数 <= 40 行；Fragment 函数 <= 60 行，必要时继续拆分）。

### 5.2 功能等价回归（手工）

1. 本地浏览：进入目录、返回上级、搜索、排序、字体大小切换。  
2. 本地文件操作：新建文件夹、重命名、删除、打开（前台/后台标签）。  
3. FTP：加载列表、打开文件、上传文件、编码诊断、错误提示。  
4. SMB：加载列表、新建目录、上传文件、重命名、删除、打开文件。  
5. 会话与状态：切换会话、恢复路径、`navigateUp()` 行为一致。  
6. 收藏与详情：添加收藏、详情弹窗字段一致。

### 5.3 性能与风险验证

1. 大目录滚动（本地/网络）时卡顿较重场景对比前后，确认日志降噪与主线程压力降低。  
2. 下载同名不同路径文件，确认缓存不再覆盖。  
3. 检查收藏落库数据，确认不再持久化 FTP 明文凭据。  

## 6. 实施顺序（执行清单）

1. 新增 `files/` 子目录下的模型与网关骨架（不接线）。  
2. 迁移纯函数与协议函数到新类，保证编译通过。  
3. `FilesFragment` 改为编排器并完成接线。  
4. 做风险点修复（日志、IO 线程、缓存命名、FTP 收藏脱敏）。  
5. 执行构建与手工回归，修复回归后收敛提交。  
