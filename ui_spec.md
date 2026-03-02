# UI 设计规格（v1.0）— MHTML/PDF 阅读器 + 文件管理器（Android，minSdk 30 / targetSdk 36）
> 目标：速度优先、低开销、可维护、健壮。  
> 说明：本规格仅描述 UI/导航/交互与状态，不涉及具体技术选型与代码实现。

---

## 1. 全局导航与页面架构

### 1.1 页面集合（Page List）
- **HomePage**（首页，应用启动默认页，根页面）
- **DirectoryModePage**（目录模式页：目录浏览主页面）
- **ReaderModePage**（阅读模式页：MHTML/PDF/网页阅读主页面）

> 说明：Home 是根；Directory / Reader 为“模式页”，从 Home 进入后独立运作。

### 1.2 从底部栏进入的全屏子页面（push 到导航栈）
- **FoldersOverviewPage**（目录会话总览，列表）
- **TabsOverviewPage**（阅读标签总览，列表）
- **MoreMenuPage**（更多菜单，列表）
- **FavoritesPage**（收藏独立页面：收藏夹树 + 收藏项列表）
- （可选）**FileDetailsPage**（文件详情页）

> 规则：Folders/Tabs/More/Favorites 均为全屏页面，通过系统返回键返回到之前页面。

---

## 2. 底部栏（Bottom Navigation，常驻）

### 2.1 按钮与顺序（固定）
1. **Home**
2. **Folders**
3. **Tabs**
4. **More**

### 2.2 行为
- 点击 **Home**：回到 HomePage（如果当前已在 Home 则不刷新状态）
- 点击 **Folders**：push `FoldersOverviewPage`
- 点击 **Tabs**：push `TabsOverviewPage`
- 点击 **More**：push `MoreMenuPage`

### 2.3 禁用/启用原则
- 底部栏始终显示、始终可点击（包括 Reader 中）。
- 任何页面都不隐藏底部栏（沉浸式不在 v1.0 范围）。

---

## 3. 导航栈规则（关键）

### 3.1 模式页定义
- **DirectoryModePage**：当前“目录会话（Folder Session）”的浏览器
- **ReaderModePage**：当前“阅读标签（Reader Tab）”的阅读器

### 3.2 返回键（Back）规则
- 在 **ReaderModePage**：
  1) 若当前标签可后退（网页历史栈）→ 执行“网页后退”
  2) 否则退出 ReaderModePage → 返回 HomePage
- 在 **DirectoryModePage**：
  1) 若当前目录不是会话根目录 → 返回上级目录
  2) 否则退出 DirectoryModePage → 返回 HomePage
- 在各 Overview/Menu/Favorites 等 push 页面：
  - Back：pop 返回上一页

---

## 4. HomePage（首页）规格

### 4.1 布局（按顺序）
1. **Recents（最近查看）**
2. **Favorites Shortcut（收藏快捷入口）**（可为一行入口或一组常用收藏夹）
3. **Local Directories（本地已授权目录）**
4. **SD Card（若存在）**
5. **Network（网络连接）**
   - **SMB**（可展开：已保存配置列表）
   - **FTP**（可展开：已保存配置列表）

### 4.2 行为
- 点击 Recent 项：在 Reader 中新建标签打开（不可达则报错提示）
- 点击 Favorites Shortcut：进入 FavoritesPage（也可通过 More → Favorites）
- 点击 Local/SD 目录入口：进入 DirectoryModePage 并定位该目录
- 点击 SMB/FTP 配置：进入 DirectoryModePage 并定位该服务器初始路径
- 新增/编辑/删除网络配置：从 Network 区块入口进入相应管理流程（具体 UI 可在 More 内承载）

### 4.3 状态
- 空态（首次启动）：
  - 引导：授权本地目录 + 添加网络配置（可跳过）
- 不可达项：
  - 历史/收藏即使不可达仍显示；点击后提示错误（不隐藏、不自动移除）

---

## 5. DirectoryModePage（目录模式页）规格

### 5.1 顶部工具栏（Top App Bar）
- 路径显示（单行省略或面包屑）
- **View Toggle**：列表/网格切换（固定在顶栏）
- 搜索入口（文件名搜索）
- 排序入口（字段 + 升/降序）
- 更多/批量操作入口（可放在顶栏或通过 MoreMenuPage 进入）

### 5.2 内容区（List/Grid）
- 仅显示：`.mht/.mhtml/.pdf`
- 每项展示（无缩略图）：
  - **Title（异步补齐）**
  - 文件名（或作为副标题）
  - 大小、修改日期（可选在第二行）
- 支持文件夹与文件项（文件夹用于继续进入目录）

### 5.3 交互
- 点击文件：在 Reader 中**新建标签**打开
- 点击文件夹：进入子目录（仍属于当前目录会话）
- 长按：进入多选模式
- 文件夹/文件的菜单项：
  - 详情（进入 FileDetailsPage 或弹层）
  - 重命名 / 删除 / 复制 / 移动
- 文件夹菜单必须提供：
  - **“在新目录会话打开”**：创建 Folder Session，并切换到该会话（DirectoryModePage 切换路径）

### 5.4 目录会话（Folder Session）
- 默认仅一个会话；新增会话仅通过“在新目录会话打开”触发
- 会话状态至少包含：
  - 来源（Local/SD/SMB/FTP）
  - 当前路径
  - 当前视图模式（列表/网格）
  - 排序字段与方向
  - 列表滚动位置（可选，尽力而为）

---

## 6. ReaderModePage（阅读模式页）规格

### 6.1 顶部工具栏
- 标题（可省略/滚动）
- 收藏（加入/移出；选择收藏夹）
- 文内查找（Ctrl+F）
- 缩放（MHTML/PDF）
- 主题：浅色/深色/跟随系统
- 更多：外部打开、分享、（网页）刷新、复制链接

### 6.2 内容区
- 显示 WebView（MHTML/http/https）或 PDF 视图

### 6.3 底部“阅读控制条”（位于内容区底部、紧贴 Bottom Nav 上方）
- **Back**（网页后退）
- **Forward**（网页前进）
- （可选）Find 快捷按钮（若已在顶栏可不放）

> 约束：Bottom Nav 四按钮布局不变；阅读控制条只在 ReaderModePage 显示。

### 6.4 链接与外链策略（行为约束）
- 打开 **MHTML**：
  - 阻止所有外链资源加载（图片/CSS/JS/iframe/网络请求）
  - 仅允许用户点击超链接：默认 **新标签**打开
- 打开 **http/https** 网页：
  - 正常加载全部资源
  - 点击链接默认新标签打开
- 不提供地址栏

### 6.5 阅读进度
- 自动保存阅读进度（百分比）
- 再次打开自动恢复到上次位置

---

## 7. TabsOverviewPage（阅读标签总览，列表）
- 全屏列表（性能优先）
- 列表项显示：
  - 标题/文件名/URL（可省略）
  - 来源类型（MHTML/PDF/WEB）
  - 关闭按钮
- 点击条目：切换到该标签并返回 ReaderModePage
- 关闭：
  - 关闭标签立即清除该标签的缓存文件

---

## 8. FoldersOverviewPage（目录会话总览，列表）
- 全屏列表
- 列表项显示：
  - 来源图标（Local/SD/SMB/FTP）
  - 路径（单行省略）
  - 最近访问时间（可选）
- 点击条目：
  - 切换当前目录会话，并返回 DirectoryModePage
- 支持关闭会话（仅关闭 UI 会话，不删除任何真实文件）

---

## 9. MoreMenuPage（更多菜单）
- 全屏列表菜单（分组显示）
- 必须包含：
  - **Favorites（收藏）** → 进入 FavoritesPage
  - Settings（设置：主题/语言/缓存/历史）
  - Export / Import（元数据导入导出）
- 可选包含：
  - Network Configs（网络配置管理入口）
  - History（历史入口，若不放 Home 可放这里）

---

## 10. FavoritesPage（收藏独立页）
- 页面结构：
  - 左侧/顶部：收藏夹树（嵌套）
  - 主区：选中收藏夹的收藏项列表
- 行为：
  - 打开收藏项：在 Reader 新建标签打开；并恢复阅读进度
  - 失效项：仍显示，点击提示错误（不修复）
- 支持收藏夹管理：新建/重命名/删除/移动（嵌套）

---

## 11. 错误与空态口径（统一要求）
- 网络断开、SD 拔出、权限丢失：条目仍展示；点击时弹出明确错误提示
- 大文件操作：显示进度、可取消、失败可重试（UI 可在任务中心/通知中呈现，细节实现另定）
- 标题异步补齐：允许先显示文件名，标题稍后刷新

---

## 12. v1.0 不包含
- WebDAV（明确暂不支持）
- 侧边抽屉（避免手势冲突）
- 缩略图/封面生成
- 全文检索（跨文件内容索引）
