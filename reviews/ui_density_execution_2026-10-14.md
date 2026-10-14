# Home / Files 紧凑布局执行记录

## 交付范围

- 依据：`35792ac` 与 `reviews/ui_density_plan_2026-10-13.md`。
- 版本：`1.0.66` / `versionCode 67`。
- D01 Home：最近/收藏默认并排；窄屏、大字体或长文案改为纵向；本地、SD、网络来源列表按 adapter 实际行高动态测量，分别最多展示 1、3、4 行，超出部分仍可滚动。SD 为空时仍保留授权入口。
- D02 Files：压缩固定顶栏；排序和文件名字号折叠到显示选项面板；idle 状态不占状态栏空间；Up 固定为 ListView 首个 header。点击/长按按 adapter item 解析，header 不作为文件；旧滚动记录仅在恢复时迁移一次，新记录保存含 header 的位置。
- 可点击当前位置、操作按钮、Up 和列表项均保留至少 48dp 的触摸目标；Files 行高至少 56dp、分隔 4dp。紧凑 dimen 仅用于 Home/Files，不改共享主题间距。
- 同步更新 `ui_spec.md`、`CLAUDE.md` 与 `PROJECT_INDEX.md`。

## 自动化验证

- UI 专项：`HomeDensityLayoutTest`、`FilesCompactLayoutTest`、`FilesScrollStateStoreTest`、`ModernUiThemeTest` 全部通过。
- 全量 JVM：`:core-base:testDebugUnitTest :app:testDebugUnitTest`，214 tests，0 failures，0 errors，2 skipped。
- 构建：`:app:assembleDebug` 成功。
- APK：`app/build/outputs/apk/debug/MHTMLReader_v1.0.66_debug.apk`（23,700,375 bytes；SHA-256 `90FA0436591FA9EB8DDFB3E85DBADB61190AABE40C1C9DF9325DD44009985490`）。
- 覆盖点包括 Home 实际 row 测高/adapter 更新/响应式快捷入口；Files 固定顶栏与列表 viewport、header 索引隔离/真实 Fragment 点击导航、空列表 header、窄屏大字体触达、旧滚动记录偏移和新记录不重复偏移。

## 设备验收待办

执行环境没有可用的 `adb` 命令，尚无真实设备截图或交互证据。以下项目不得视为已验收：

- 360dp × 640dp 内容区下 Home/Files 前后截图及具体像素高度对比。
- 320dp、fontScale 1.5/2.0、横屏、软键盘打开/收起与所有操作可达性。
- Home 外层滚动与来源列表内层滚动手势。
- LOCAL 根/子目录/无权限、FTP/SMB 根/子目录、空目录、无搜索结果时的 Up 和 header 行为。
- 排序/字号切换后的首项点击与长按、快速会话切换时滚动恢复，以及网络慢请求下旧结果不覆盖新目录。
- 浅色/深色主题设备实测。

## 工作区说明

APK 从当时的共享工作区构建；该工作区同时含有尚未提交的 V03 业务修改，因此 APK 不应被描述为仅含 UI 的隔离构建。Git 提交范围按本轮 UI 文件和必要的 UI 文档/版本更新隔离，不包含 V03 业务文件。
