# MHTML 敏感 URL 日志修复执行记录

## 范围

- 计划卡：B01 / M2，来自 `reviews/mhtml_review_plan_2026-10-14.md`。
- 版本：`1.0.67` / `versionCode 68`。
- 在 `BlockingResourceWebViewClient` 的 `blocked_resource` 与 `blocked_navigation` 日志中，不再输出完整 URL。
- 新增 `SafeUrlLogSummary`：只保留 URI scheme 和经 ASCII 字符白名单校验的 host；path、userinfo、query、fragment 和 data payload 均不进入日志。畸形 URI 使用固定 `target=unavailable`，带编码控制字符的 host 不记录。
- MHTML fallback reload 日志使用同一摘要，不再记录完整本地缓存 URL。
- 保留拦截策略、HTTP 手势外链处理、资源响应和日志事件语义。

## 验证

- 新增 6 项日志回归：resource userinfo/path/query/fragment、navigation userinfo/path/query/fragment、畸形 URL、data payload、host 控制字符、本地 cache URL 路径。
- 修复前新增日志用例中 4 项失败，确认完整 URL 会进入日志；实现后拦截器专项测试 17 项全部通过。
- JDK 17 全量 JVM：`:core-base:testDebugUnitTest :app:testDebugUnitTest`，220 tests，0 failures，0 errors，2 skipped。
- 构建：JDK 17 执行 `:app:assembleDebug` 成功。
- APK：`app/build/outputs/apk/debug/MHTMLReader_v1.0.67_debug.apk`，23,316,307 bytes；SHA-256 `A9794198BB9DAF59F9831B5645D6E11AEEDEBF631A0C44FEFAA66BF8E51D5C44`。

## 设备与工作区说明

- 未连接 Android 设备；未做真实 WebView/logcat 设备验收。
- APK 从包含未提交 V03 业务改动的共享工作区构建，不能称为只由 B01 提交独立重现。B01 的 Git 提交仅纳入本卡源码、测试、版本和文档；V03 文件继续留在工作区。
- 下一步按计划处理 B02（Files 操作期间 Up/系统返回的状态一致性）；B03 阅读进度恢复仍排在其后。
