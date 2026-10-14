# MHTML 阅读进度保存与恢复执行记录

## 范围

- 计划卡：B03 / M1，来自 `reviews/mhtml_review_plan_2026-10-14.md`。
- 版本：`1.0.69` / `versionCode 70`。
- WebView tracker 仅在历史位置恢复结束后启用采样；500ms debounce 保留。
- `stopAndFlush()` 在暂停、切 tab、换内容和销毁前同步从当前 WebView 取最终快照，并取消待执行 debounce；写入只带不可变 `WebProgressSnapshot`，不将已销毁的 WebView/Fragment 捕获进应用级写任务。
- `WebViewProgressWriter` 使用 ReaderRuntime 应用作用域，串行写历史；相同 source 的过期排队快照会被跳过，正在进行的旧写完成后较新的快照再写入。恢复读取前等待已排队写入完成。
- 历史读取按 `ReaderTab.sourcePathRaw` 精确查询。恢复请求校验 tabId、内容 generation、目标 URL 和 view 生命周期；A→B→A 时旧 A 回调不能修改新 A。
- 恢复按当前 WebView contentHeight、viewport 和 scale 计算目标位置，最多等待 24 次，每次 50ms；连续 5 次滚动范围稳定后应用。0/短页恢复到顶部。恢复滚动期间关闭采样，恢复完成后以当前滚动位置作为采样基线；恢复期间用户触摸会取消自动定位。
- MHTML fallback reload 会先尾写旧位置并暂停采样，避免布局重载的顶部事件覆盖历史。
- 增加 `HistoryDao.saveWebProgress` 原子 UPSERT：history 行尚未由 `recordOpen` 创建时仍能保存尾进度；冲突时保留原标题/类型，只更新进度、页码与 lastAccess。未改实体、schema version 或 Migration，也未改变 PDF 页码写入口。

## 自动化验证

- 定向通过：`WebViewProgressTrackerTest`、`WebViewProgressWriterTest`、`WebViewProgressRestorerTest`、`HistoryDaoTest`。
- 全量 JDK 17 JVM：`:core-base:testDebugUnitTest :app:testDebugUnitTest`，240 tests，0 failures，0 errors，2 skipped。
- 两项 skipped 是 `LocalFileSystemRenameTest` 中符号链接与 dangling symlink 用例；执行环境无法创建目标 symlink。
- JDK 17 构建：`:app:assembleDebug` 成功。
- APK：`app/build/outputs/apk/debug/MHTMLReader_v1.0.69_debug.apk`，23,744,219 bytes；SHA-256 `B6D334AEF28AA0F7223613C22863CDD4762DFF9B32B9DC7B9EC9979BB67543B5`。

## 设备验收待办

- SDK adb 可用但没有连接设备，未进行真实 WebView/logcat 验收。
- 必须在设备上验证本地 MHTML 与 FTP/SMB 下载后的 MHTML：滚动后 500ms 内切 tab/暂停/关闭仍尾写；A→B→A、旋转重建能恢复；慢加载/迟到图片后位置比例合理；短页与 0/100% 边界；同 URL 不重载时保留当前滚动；MHTML 桌面布局 fallback 不回零。
- 当前自动化覆盖恢复算法、late contentHeight 稳定等待、A→B→A generation guard 和尾写/写入顺序；不等于真实 WebView 的图片、缩放与渲染时序验收。

## 工作区说明

- APK 来自含未提交 V03 业务修改的共享工作区，不应描述为仅由 B03 提交可独立重现。
- 本次 Git 提交白名单只包含进度恢复代码、测试、版本与执行/索引文档；V03 文件继续保留未提交。

## 生命周期补充修复

- 后续复核发现：旧 `stopAndFlush()` 虽会解绑滚动 listener，却没有关闭 tracker 的 `samplingEnabled`；`onResume()` 重挂 listener 后，可能在历史恢复完成前就恢复采样。
- 修正 `stopAndFlush()`：先关闭采样再尾写，并清理无 listener 时残留的采样状态。新增 `WebProgressSamplingState`，以内容 generation 记录恢复是否完成及 Fragment 是否 resumed。恢复未结束时回前台不会采样；恢复在后台结束时延迟到 `onResume()` 启用；已恢复内容暂停后再返回则重新启用。旧 generation 的迟到完成不能打开新内容采样。
- `WebViewProgressTracker.enableSampling()` 变为幂等，重复生命周期通知不会重置现有采样基线。
- 新增 `WebProgressSamplingStateTest` 与 tracker 生命周期用例，覆盖恢复/暂停两种先后次序、旧代次回调，以及 listener 重挂后必须显式启用采样。
- 生命周期修复版本：`1.0.70` / `versionCode 71`。JDK 17 全量 JVM 测试：245 项，0 失败、0 错误、2 跳过；跳过项仍为两个 symlink 主机能力测试。`:app:assembleDebug` 成功。
- APK：`app/build/outputs/apk/debug/MHTMLReader_v1.0.70_debug.apk`，23,365,459 bytes；SHA-256 `7A0A735046329E84811D84EFA0AF43BE84BCBA45D3094C675D7EE5AC6DD7FF97`。该 APK 是含未提交 V03 改动的共享工作区构建，不代表仅由本次修复提交独立重现。
- 该修复不改变 Room v4、PDF 路径或上传路径。真实设备验收仍待进行；APK 仍由含未提交 V03 改动的共享工作区构建。
