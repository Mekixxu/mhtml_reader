# MHTML 修复执行计划：供执行 LLM 使用

## 0. 授权与基线

- 用户要求本 agent 仅审查与制定计划，实施交给其他 LLM；本文件不是已完成修复的声明。
- 审查报告：`reviews/mhtml_current_review_2026-10-15.md`，提交 `fb48c56`。
- 功能基线：`b2c8fe1`，`versionName=1.0.71`、`versionCode=72`。开始实施前重新核对版本和 HEAD。
- 仅处理报告 Q1-Q4：MHTML 阅读接管、缓存完整性、缓存保护交接、并发容量。PDF、上传、备份/收藏新契约、Hilt 启用均不在范围内。
- 不分析 `bundle_*.txt` 和 `apply_bundle.py`，不删除现有功能，不重置或代提交其他 agent 的改动。
- 当前未提交 V03 涉及备份、网络凭据游标迁移、本地 rename 及相应测试；这些内容不属于本批修改。不要用 `git add .`。
- 构建显式使用 JDK 17。功能批次按仓库规则递增版本、构建 APK、更新记录并提交。

## 1. 执行顺序

| 批次 | 任务 | 前置 | 交付 |
|---|---|---|---|
| A | T01：Q1 手势接管 | 无 | 独立修复、正式回归、APK |
| B | T02：Q4 缓存完整性 | A 验收通过 | 独立修复、正式回归、APK |
| C | T03：Q2/Q3 缓存协调 | B 验收通过；先写所有权说明 | 租约与预算整体闭合、并发回归、APK |
| D | T04：总回归与交接 | A-C 完成 | 测试统计、设备待验、最终执行记录 |

不能只完成 helper 就标记批次结束；必须检查实际 Runtime 和所有调用方。
批次 C 可分小提交，但存在保护交接空窗或未接线预算时，不得作为“Q2/Q3 已修复”的发布交付。

## 2. 开始前检查

1. 读取 `CLAUDE.md`、审查报告及涉及模块的 `build.gradle.kts`。
2. 保存 `git status --short` 清单；每次提交前对比，保护其他 agent 文件。
3. 确认 `core/cache` 属于 core-domain、`core/reader` 属于 feature-reader；app 的网络下载最终以 LocalFile 进入 Reader。
4. 搜索所有 `CacheOpenManager`、`CacheEvictor`、`TabCacheRegistry`、`OrphanCacheCleaner` 构造点及调用方，记录真正运行的 Runtime/Worker 路径。不要仅修改未聚合的 Hilt binding。
5. 基线测试：253 项，251 通过、2 跳过。此数字来自共享工作区，不是固定验收目标；新增测试后报告实际数量。

## 3. T01：Q1 有效手势接管

### 允许范围

- `app/src/main/java/html_reader/ReaderFragment.kt`
- `core/reader/web` 下必要的小型手势/采样状态类
- 对应 app Robolectric 测试、索引和交付记录

不修改 Writer、历史 DAO、恢复百分比公式或外链拦截策略。

### 必须保持的行为

- 恢复失败不开采样；轻触或长按抖动不能覆盖旧位置。
- 有意拖动后不再强制回拉到旧位置，仍能保存用户的新位置。
- pause/resume、切 tab、销毁视图继续按 generation 防护。
- 不消费原本应交给 WebView 的触摸事件，不破坏点击链接、缩放、长按图片。

### 实施步骤

1. 将生产触摸监听器纳入回归。旧实现只判断 ACTION_MOVE，不能再用测试直接调用 `onRestoreResult(USER_TOOK_CONTROL)` 代替该路径。
2. 记录 ACTION_DOWN 坐标及当前内容 generation；ACTION_CANCEL/ACTION_UP 清理手势状态；新内容也清理。
3. 使用 `ViewConfiguration.scaledTouchSlop` 判定位移，不使用固定 1px 或任意 MOVE。
4. 将“用户操作取消自动定位”和“当前内容可安全采样”分别判断。有效内容尺寸缺失时不要仅凭移动就标记恢复完成。
5. 小型状态类保持平台无关、可单测；Fragment 负责读取 ViewConfiguration、WebView 尺寸及接线。
6. 不让旧手势、后台回调或旧 tab 开放新内容采样。

### 必须补齐的正式测试

- history=0.6，页面有效但未恢复，down + 小于 slop 的 move + up，scrollY=0，pause 后仍为 0.6。
- 相同前置，超过 slop 的有效拖动后保存新位置，不自动回拉。
- 恢复超时后轻触不写 0；真正拖动仍能接管。
- CANCEL、切 tab、generation 更新、零尺寸移动不能提前放行。
- 已有恢复超时、迟到尺寸、取消、pause/resume 回归继续通过。

### 不接受

- 移除尾写、禁用所有触摸接管、让失败后永远不保存。
- 只延长等待时间或将 ACTION_MOVE 改回 ACTION_DOWN。
- 为通过测试删掉真实数据库/生产监听器链路。

## 4. T02：Q4 完整缓存才可成功

### 允许范围

- `core/cache/CacheOpenManager.kt`、必要缓存结果模型
- `core/reader/tab/DefaultReaderTabManager.kt` 及实际调用方
- 类型化错误、对应测试、索引和交付记录

### 实施步骤

1. 表达已知长度与未知长度：推荐 `Long?`，`null` 未知，`0` 为已知空文件。检查所有调用方，不能把 SAF 原有的 0 直接改成“必须为空”。
2. 先写本次专属临时文件，输出关闭成功后校验实际字节数；已知长度不相等就失败。
3. 已知长度的源增长也应视为变化，避免无限写入；每块写入前做取消/大小检查。
4. 校验完成后提交最终文件；失败和取消只清理本次临时文件，不删除其他持有者的有效缓存。
5. 上报实际 copied，不再无条件伪造 total/total；调用方只对明确完整结果创建 Ready。
6. 缓存命中必须只命中已提交成品，不能复用 `.part`。保持原扩展名、版本 key 和 MHTML 打开方式。
7. CancellationException 重抛，不在取消状态中继续 emit；异常路径不能落入末尾成功。
8. 输出 close/flush 失败不能吞掉后仍宣布成功；保持主异常语义并清理临时产物。

### 必须补齐的正式测试

- 长度采样 100，打开输入流时原地截为 60：必须 Error，无 Ready，无可复用半成品。
- 预计 100/实际 100 成功；预计 100/实际增长到 140 不得假成功。
- 已知空文件与未知长度分别有测试；不把“字节复制完整”误称为“MHTML 语法有效”。
- read/write/flush/close 失败与取消清理；原有有效缓存不被失败重写损坏。
- 正式测试串联 TabManager，而不只断言某个 helper 抛异常。

### 不接受

- 仅删除末尾 100% 事件，却继续返回 Ready。
- 仅在 ReaderFragment 检查文件大小，留下其他调用方错误。
- 把所有未知长度源直接拒绝，或为追求完整性删除现有支持入口。

## 5. T03：Q2/Q3 连续保护与全局预算

这是一个跨对象资源所有权任务，不应机械地在现有函数上分别增加互斥锁。

### 允许范围

- `core/cache`：缓存协调对象、租约、预算、淘汰器和 registry
- `core/reader/tab/DefaultReaderTabManager.kt`
- `ReaderRuntime`、实际共享缓存构造方、App 维护 Worker 的必要接线
- 对应测试、索引、执行记录

如发现需要更改数据库 schema、收藏/备份身份或上传行为，暂停并说明，不扩大范围。

### C0：先写所有权说明

在执行记录中明确以下四点，再开始改代码：

1. 同一 cacheRoot 的唯一协调对象由谁创建、所有打开和维护入口如何共享。
2. 拷贝开始、历史查询、去重、发布、取消、关闭各阶段由谁持有 lease。
3. 预算如何核算已提交文件、写入中的文件及尚未落盘的预约，如何避免双计/漏计。
4. 何时持锁、哪些函数可挂起、释放是否幂等、关闭和删除的顺序。

不要求引入新框架；保留当前 Runtime 装配，不启用 Hilt。

### C1：连续缓存 lease

1. 拷贝开始或缓存命中时取得保护，Flow 结束不得自动撤销尚待交给标签的保护。
2. 历史读取失败/取消时释放本次 lease；历史查询期间 lease 必须一直存在。
3. 在状态同步区内先把 lease 移交 registry，再发布 tabs/current；不得先发出可观察状态、再补 bind。
4. 去重返回已有 tab 时释放多余 lease，不删除已有 tab 的缓存。
5. 关闭标签时先撤销可观察状态和所属引用；最后一个引用消失后才允许清理文件。
6. 淘汰与保护状态检查必须同步。不能先取 activeKeys 快照、随后允许新保护建立、最后仍按过期快照删除。
7. 在途锁表若做回收，必须处理等待者，不能一 unlock 就删除锁条目让第三个请求创建新锁。若暂不回收，要如实记录边界。

### C2：预算 reservation

1. 不同 key 共用同一预算协调对象，不用每个打开请求单独创建预算。
2. “检查可用量、选择淘汰项、核对实际删除、登记预约”作为一致操作完成。
3. 核算不变量：受管理已提交字节 + 在途完整预约总量不得超过现有允许上限；在途文件已经落盘的部分不能再加一次。
4. 已知大小在首块前预约；未知大小按块在写入前扩容。源增长/失败/取消释放相应预约。
5. 提交时将预约转为已提交占用，不能先释放预约再稍后登记文件。
6. 删除失败不算释放空间；需要能返回 CacheBusy/CANNOT_FREE 等既有类型化错误。
7. 不在长时间网络/流复制期间持有全局互斥锁；锁保护记账与所有权变更，复制使用已取得的句柄。
8. 不静默改变当前“文件大小等于上限也拒绝”的边界政策；本批首先保持现有语义，若要放宽需说明并取得批准。
9. FTP/SMB 中转区尚有额外占用。除非另获授权，本批不重写下载缓存策略，也不把 app_cache 限额误称为整个应用的磁盘硬上限。

### 必须补齐的受控并发测试

- Q2：A 复制完成并挂起 history，B 要腾空间；A 的文件不能被删，最后 Ready 必有文件。
- Q3：空预算 200，两份 150 在首块前同时预约；最多一个获准，不能出现实际 300。
- 两次相同 key、不同 key、去重、发布后立即关闭、历史失败、取消及异常都不会泄漏 lease/reservation。
- 维护清理与新 lease 并发，不能凭旧快照删掉已受保护文件。
- 未知长度分块增长、删除失败、磁盘失败后重新预约可继续使用。
- ReaderRuntime 实际传入共享协调对象；只测一个手工 new 的 helper 不算接线验收。

用 CompletableDeferred 或可控 dispatcher 建立时序；不要 Thread.sleep、不要靠循环多跑碰概率。

### 不接受

- 仅增加一次 `file.exists()` 检查。
- 仅提前 `bind`，但仍留在 Flow finally 到 bind 之间的空窗。
- 仅给 `makeRoomFor` 加 Mutex，检查完释放锁、写入却没有 reservation。
- 把所有文件永远加入保护集合，导致缓存永远不能回收。
- 用全局锁串行整个下载/复制来掩盖问题，不说明对性能的影响。

## 6. T04：验证与交付

### 自动化命令

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :core-base:testDebugUnitTest :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
git diff --check
```

先运行对应专项，最后每批跑完整测试。记录 tests/failures/errors/skipped 实际值及跳过原因，不只写 BUILD SUCCESSFUL。

### 现成审查实验

`C:\Users\Player_X\AppData\Local\Temp\mhtml-review-20261015\ReviewEvidenceTest.kt` 含四个复现探针，结果归档为同目录 `evidence.xml`。

注意：探针断言的是错误行为成立。执行者可以参考搭建方法，但正式回归应反转到正确期望，并纳入仓库；不能直接复制 probe 后以“4 项通过”宣布修复。临时目录可能被系统清理，若不存在，根据审查报告中的屏障时序重建。

### 设备清单

- 本地、SMB、FTP 各打开 MHTML 并验证中部进度恢复、快速离页尾写。
- 轻触、抖动、长按、真正拖动、缩放不误覆盖/回拉；后台返回正常。
- 后台连续打开多个文件、关闭其中一个、同时进行维护，仍能读取其他标签。
- 文件变化/磁盘不足显示失败或忙碌，不出现“完成”后空白。
- Files 搜索、Display、路径详情、列表内 Up 不退化。

没有设备或服务器就明确“未执行”，不要用 Robolectric 代替签字。

### 每批提交清单

1. 本批功能/测试文件及必要 Runtime 接线。
2. 按实际最新版本递增，生成 `MHTMLReader_v<versionName>_debug.apk`。
3. 更新 `CLAUDE.md`/索引中的新增目录职责；更新统一执行记录，分别列出 Q1-Q4 状态。
4. 只暂存本批明确文件，保留其他 agent 的 V03。
5. 记录 commit、APK 路径、校验值、共享工作区是否含未提交代码。
6. 未闭合项写清原因和下一步，不凭单测全绿把设备待验或其他任务一并标为完成。
