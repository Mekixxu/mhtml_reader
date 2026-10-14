# Files 操作期间导航状态修复记录

## 范围

- 计划卡：B02 / U1，来自 `reviews/mhtml_review_plan_2026-10-14.md`。
- 版本：`1.0.68` / `versionCode 69`。
- FilesFragment 维护显式 `operationButtonsEnabled` 状态；目录路径刷新和远程 browse 准备不再用默认 `true` 覆盖先前禁用状态。
- Up 按钮统一按 `operationButtonsEnabled && !operationRunning && canNavigateUp` 更新。操作锁获取/释放时刷新 Up 可用状态。
- 锁或禁用状态期间，Up 与系统 Back 共用 `navigateUp()` 守卫：返回“已处理”但不改变路径，避免 MainActivity 将 Back 解释为离开 Files 页面。
- 文件夹行点击在导航被阻止时不加载新目录；释放锁并恢复按钮状态后仍按本地/FTP/SMB 原有父路径规则导航。
- FTP/SMB 根路径且无配置时仍不可 Up。未改传输、上传策略、header offset、滚动恢复和会话持久化。

## 验证

- 新增回归覆盖：
  - 操作按钮禁用后经过 `updateCurrentDirLabel()` 与 `prepareRemoteBrowse()`，Up 仍禁用；被阻止的 Up 不改变路径。
  - 锁期间直接调用 Up 和点击目录行均不切换目录；释放后 Up 正常恢复。
  - 锁期间系统 Back 在子目录和根目录都留在当前 Files 页面，不上行、不跳转 overview。
  - FTP/SMB 根路径、缺少网络配置时刷新后 Up 仍禁用。
- 修复前 3 项关键测试失败；修复后 `FilesCompactLayoutTest` 全部通过。
- 全量 JVM：JDK 17 执行 `:core-base:testDebugUnitTest :app:testDebugUnitTest`，225 tests，0 failures，0 errors，2 skipped。
- 构建：JDK 17 执行 `:app:assembleDebug` 成功。
- APK：`app/build/outputs/apk/debug/MHTMLReader_v1.0.68_debug.apk`，23,700,539 bytes；SHA-256 `AED2CBE4D0C2847516DB5818130106A3CFFC541E00C22ADE1A2C0A8EF4E11B81`。

## 设备与工作区说明

- 本次未做真实设备/网络服务器验收；FTP/SMB 根态仅以本地 UI 状态回归验证。
- APK 从包含未提交 V03 业务改动的共享工作区构建，不能称为只由 B02 提交独立重现。Git 提交仅包含 B02 源码、测试、版本和文档；V03 文件继续留在工作区。
- 2 项跳过为既有本地符号链接冲突测试，当前主机不具备创建目标符号链接的条件。
- 后续按主线推进 B03：MHTML 阅读进度尾写与跨 WebView 恢复。
