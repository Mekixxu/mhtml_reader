# R1 与 Files 顶栏交付记录

## 范围

- 用户授权直接修复 `mhtml_review_2026-10-15.md` 的 R1。
- 同步修复 Folders -> Browse files 的 Search、Upload/新建、路径、Display 高度与列对齐，去掉搜索圆角框底部细线。
- 保留 MHTML 本地及 SMB/FTP 下载打开主线；未修改 PDF、上传业务、数据库结构或其他 agent 的 V03。
- 版本：`1.0.71 / versionCode 72`，基线 `e8fa13e`。

## R1 修复

原流程在尺寸等待返回 null、异常或取消后仍由 finally 标记恢复完成，后续暂停尾写可能把原进度覆盖为顶部 0。

1. `WebViewProgressRestorer.restore` 显式返回 APPLIED、USER_TOOK_CONTROL、DIMENSIONS_UNAVAILABLE、SUPERSEDED。
2. `WebProgressSamplingState.onRestoreResult` 仅接受成功恢复和用户接管；失败不会标记完成，暂停/恢复也不会绕过保护。
3. Fragment 的 finally 仅释放失败请求，不再启用采样；取消仍重抛，异常日志仅保留异常类型。
4. 无有效尺寸时最多等待三个窗口，每窗口 24 次、间隔 50ms，窗口之间间隔 250ms。尺寸等待总上限约 3.95 秒，不含数据库等待。
5. 失败 claim 可以释放；同 generation 最多两个 claim，避免重复 onPageFinished 无限重试。成功 claim 保留，重复回调不会再次定位。
6. 触摸接管从 ACTION_DOWN 改为 ACTION_MOVE：轻触不会把失败恢复误判为完成；主动拖动时停止自动定位并开启当前内容采样。有限重试耗尽后仍可拖动接管，不会永久失去保存能力。
7. 当前 tab、generation、目标 URL、view 存活检查继续生效；后台恢复成功仅记录完成，前台恢复时再启用采样。

编译归属不变：`core/reader/web` 由 `feature-reader` 映射编译；Fragment、集成测试位于 app。Runtime 和历史 UPSERT 保持原装配。

## UI 修复

- 两行采用 TableLayout 共享列宽：Search/路径左列边缘对齐，Upload/新建与 Display 右列同宽。
- 四个控件及搜索输入统一 48dp 高度；保留 48dp 触摸目标、单行搜索和原蓝绿色浅深主题。
- FilledBox 普通/聚焦线宽均为 0，搜索提示移入输入框，不再占浮动标签高度；保留 clear_text。
- 两个按钮显式使用 MaterialButton，并去掉上下 inset，使可见填充区域与相邻控件对齐。
- 路径保留中间省略、点击详情、原背景和语义；搜索、路径、按钮采用 14sp，搜索/路径去掉额外字体留白。
- Display 仍默认折叠，排序/字号选项及列表内 Up、列表滚动契约未变。

## 自动化验证

JDK 17，下列命令均成功：

```powershell
.\gradlew.bat :core-base:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug
.\gradlew.bat :core-base:testDebugUnitTest :app:testDebugUnitTest
```

- 最终 253 项：251 通过、2 跳过、0 失败/错误。app 230 项，core-base 23 项。
- 两个跳过项来自当前主机的符号链接支持限制，属于原 V03 测试。
- 新增 8 项测试：恢复超时、迟到尺寸、接管/旧代次、claim 上限、协程取消、两条 Fragment/真实 Room 集成、顶栏几何回归。
- 集成测试调用真实 Fragment 恢复方法、Activity 暂停/恢复回调、Tracker/Writer 与 Room DAO。旧进度 0.6、初始尺寸无效；超时后尺寸有效也不会写 0；第二窗口有效后恢复并尾写 0.6。
- Robolectric 不运行真实 Chromium；测试 WebView 注入内容高度和 scale，并显式设置视口边界。集成覆盖不等同于真实 MHTML 加载验收。
- 顶栏测试覆盖 320dp、浅/深色、fontScale 1/1.5/2：四项等高、列边缘对齐、搜索宽度不小于 96dp、聚焦/普通线宽为 0。
- 原列表占比、48dp 触摸目标、Up/系统返回操作态等回归继续通过。

## APK 与工作区

- APK：根目录 `MHTMLReader_v1.0.71_debug.apk`，23,365,679 字节。
- SHA256：`0115E357FD64790E550CFA0CB2978070C0057FFCE47AE43C773631FF248BB6D7`。
- APK 与测试来自共享工作区，包含其他 agent 尚未提交的 V03 代码；不是本次提交的隔离构建。
- V03 文件未撤销、未暂存、未代提交；本次提交仅包含 R1、顶栏、版本、测试与交付文档。

## 设备待验

adb 可用但无连接设备，本次未执行真实 WebView/截图/设备测试：

1. 本地及 SMB/FTP 下载 MHTML，记录中部进度，关闭/重开、快速切标签、后台再返回，确认位置一致。
2. 旧进度非零且页面尺寸晚于首窗口就绪：不轻触时自动恢复；所有窗口失败后离页不能写顶部 0，再拖动应允许保存新位置。
3. 恢复等待中拖动、旋转或销毁页面，确认不回拉、不出现旧代次定位/尾写。
4. Folders -> Browse files 的本地新建、FTP/SMB Upload 文案、Search 输入/清除、路径详情、Display 展开与 Up 功能。
5. 浅深色、横屏、键盘显示、字体 1.5/2 倍：圆角搜索底部无细线，四项可见高度/边缘对齐，文字不裁切，列表显示区不退化。
6. TalkBack、键盘及非触摸滚动在异常恢复场景下的接管行为仍需设备验证。
