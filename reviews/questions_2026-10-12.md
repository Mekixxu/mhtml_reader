# mhtml_reader 执行方问题清单（2026-10-12）

> 交接对象：GPT 6.1 Sol / 高阶复核
> 生产基线：`6733f7c`，应用 `1.0.61 / 62`，Room v4
> 本轮提交范围：`bf599d3..6733f7c`
> 本轮只回答问题、请求决策，不代表任何任务已获最终验收

## 0. 当前执行状态

### 0.1 已完成（执行方判定）

| 任务 | 状态 | 提交 | 说明 |
|---|---|---|---|
| N01 关闭不可靠下载复用 + 错误类型 | 实现完成 | `5e0d052` | 已移除按长度复用；`AppError` 透传 |
| N02 收藏图线性导入 | 已完成 | `80c578a` | `FavoriteImportPlanner` O(n+e)，断链/环挂根测试 |
| T08 名称校验 / 重命名不覆盖 | 已完成 | `1ef08a0` | 本地/SMB 名称校验、冲突错误文案 |
| T12 凭据迁移 CAS | 已完成 | `d896904` | 只更新 password 列的 CAS |
| T07 传输取消/超时 | 已完成 | `c7bb7ea` | 分块取消、FTP/SMB 超时、`.part` 清理 |
| N04 备份取消边界 | 已完成 | `4b20bc7` | suspend 分块读写、取消测试 |
| N03 PDF 所有权/退出闭环 | 实现完成 / 待复核 | `f3c31fd` | R3+R4 已改，但专项测试与设备验收未闭合 |

### 0.2 已验证

- `:app:testDebugUnitTest`：148 项通过
- `:core-base:testDebugUnitTest`：23 项通过
- 合计 **171 项，0 失败**
- `:app:assembleDebug` 通过，产物 `MHTMLReader_v1.0.61_debug.apk`

### 0.3 环境限制

- 无可用 Android 模拟器/真机，`connectedAndroidTest` 未跑
- 无可用 FTP/SMB 服务器，真实网络端到端未验证
- Robolectric 无法生成可用 `PdfDocument`，无法直接测真实 `PdfRenderer`
- Robolectric 不支持 AndroidKeyStore，非空密码的加密路径无法直接跑单测
- lint 未在本轮成功运行

---

## 1. N03 PDF 所有权与退出闭环（最高优先级问题）

### Q1.1 R3 取消交接实现是否可接受？

当前实现（`AndroidPdfReaderController.renderPage`）：

```kotlin
var unclaimed: Bitmap? = null
try {
    val rendered = withContext(io) {
        mutex.withLock {
            renderLocked(...).also { unclaimed = it }
        }
    }
    unclaimed = null
    return rendered
} finally {
    unclaimed?.let { if (!it.isRecycled) it.recycle() }
}
```

请确认：

1. 该模式是否能覆盖 `withContext` prompt cancellation 导致的“返回值被丢弃、Bitmap 未被调用方接收”窗口？
2. 是否存在 double-recycle 风险？尤其是 `renderLocked` 内部 render 失败已 recycle、外层 finally 再访问的情况。
3. 零页 fallback bitmap 是否也应走同一 `unclaimed` 路径？当前实现是否已覆盖？
4. 是否需要增加 `NonCancellable` 仅用于回收动作？当前 finally 同步执行，是否足够？

### Q1.2 R3 的自动化测试应如何设计？

当前 Robolectric 无法注入 fake `PdfRenderer`，也无法在真实 PDF 上复现“IO 产出 Bitmap 后、主 dispatcher 恢复前取消”。

可选方案：

- A. 增加内部 `PdfRenderHandoff` 辅助类，用可控 `Dispatcher`/latch 单测交接语义；
- B. 给 `AndroidPdfReaderController` 注入 renderer/descriptor 工厂，仅测试用；
- C. 只做代码审查，不新增测试，标记设备待验；
- D. 其他。

请给出推荐方案与最小实现结构。如果选 B，请说明是否需要把 `PdfRenderer` 抽象成接口，以及如何保留 `page.use` 与现有 Mutex。

### Q1.3 R4 close/open 有序链是否足够？

当前实现：

- `ReaderRuntime.closePdfControllerAfter(previous, controller)`：应用 scope 内先 join 上一个 close job，再 close；
- `ReaderFragment.pdfExitJob` 串起退出任务；
- 切 Web/空标签/无效 PDF 时调用 `schedulePdfControllerExit()`；
- 下一次 `open` 前 `awaitPdfControllerExit()`；
- PDF→PDF 仍依赖 `controller.open` 内部 `closeInternal()` 与 Mutex。

请复核以下边界：

1. PDF A 慢 → 快速切 Web → 快速切 PDF B：
   - B 的 open 是否一定等到 A 的 close/退出链完成？
2. PDF A → PDF B 快速切换：
   - 不额外 schedule close，直接让 B 的 `open` 覆盖 A，是否仍可能出现 A 的迟到清理关掉 B？
3. 视图销毁时旧 controller 与新视图 controller 是否可能被同一个 close job 混淆？
4. `pdfExitJob` 链是否会无限增长或保留已销毁 controller 引用？是否应在完成时置 null？

### Q1.4 是否需要更完整的“内容切换协调器”？

Review 原建议是：

```kotlin
cancelPreviousPdfJobs()
awaitPreviousPdfJobs()
controller.close()
resetPdfPreview()
```

当前实现是“退出链 + open 前 await”，不是单一串行 actor。请判断：

- 是否满足 R4 的验收意图？
- 还是必须重构为单一 `Mutex`/actor 串行化所有 PDF 内容切换？
- 如果当前方案可接受，哪些测试可以证明顺序（fake controller + barriers）？

### Q1.5 N03 的完成状态

当前计划中 N03 标记为 `实现完成/需复核/设备待验`。请确认：

- 在无设备环境下，是否应保持一致，还是可以标记为“实现待合入”？
- 需要补哪些自动化测试后才能从复核状态转为正式完成？

---

## 2. N04 / R5 备份阻塞取消

### Q2.1 阻塞 read 的取消保证

当前 `BackupStreamIo`：

- 分块前后检查 `ensureActive()`
- 成功/失败/取消都会 `use { close() }`
- 没有实现“从外部关闭阻塞流”的机制

因此：

- 若 `read()` 卡在慢 SAF provider，取消后只能等本次 read 返回；
- 我们已在注释中记录，不宣称即时取消。

请确认：

1. 是否必须实现 latch + 关闭流解阻塞的自动测试？
2. 推荐用 `runInterruptible`、`invokeOnCompletion { close() }`，还是接受当前“协作式取消 + provider 超时”的限制？
3. 若实现外部关闭，如何避免与 `use` 的 close 竞争导致异常被包装？

### Q2.2 导出内存成本

`writeUtf8` 仍一次性 `text.toByteArray()`。当前只做了分块写出，没有改变导出格式或内存峰值。

请判断：

- 这是否仍违反 R5 的“内存成本如实记录”要求？
- 是否需要改为 `Reader`/分段写出 UTF-8，以避免大 JSON 的完整 ByteArray 峰值？

---

## 3. T07 / F08 传输取消与上传半成品

### Q3.1 上传部分文件的提示

当前处理：

- 中途取消/失败不会再报成功；
- 但不会删除远端半成品；
- UI 目前只有通用上传失败文案，没有“可能已部分上传”的提示。

请确认：

- 是否需要新增 `FilesTransferException.PartialUpload` 或独立提示文案？
- 判定“可能部分上传”的边界是什么：已写入至少 1 字节？已打开远端 output stream？
- 是否只需在文档里记录，不加强制 UI 提示？

### Q3.2 SMB / SmbFile 资源关闭

当前：

- `SmbFile` 设置了 `connectTimeout` / `readTimeout`；
- 下载/上传使用 `inputStream` / `outputStream` 的 `use`；
- 但 `SmbFile` 本身实现 `AutoCloseable`，当前没有显式 `SmbFile.close()`。

请确认 jcifs-ng 语义下：

1. 是否必须在流关闭后再 `SmbFile.close()`？
2. 还是关闭 stream 已足够释放会话资源？
3. 若必须 close，推荐 `SmbFile.use { ... }` 还是 finally 中显式关闭？

### Q3.3 `copyCancellable` 回退语义

当前：

- 每块前检查取消；
- 阻塞中的 socket read 不会立即返回；
- 超时负责最终退出。

请确认：

- 是否需要在 T07 中进一步使用 `runInterruptible`；
- 还是当前“协作取消 + 超时”就是预期契约？

### Q3.4 `writeAtomically` rename 后的取消

当前顺序：

1. 写 `.part-*`
2. 检查取消
3. rename 覆盖目标
4. 返回

如果在 rename 成功后、函数返回前发生取消，调用方会收到取消信号，但目标文件其实已成功替换。请判断：

- 这是否属于可接受竞态？
- 是否需要把 rename 放在 `NonCancellable` 中并保证成功后不再抛取消？

---

## 4. N01 / R1 网络验收

### Q4.1 无服务器时的完成标准

当前 N01 已删除按长度复用、补齐身份键，但无法验证：

- 真实 FTP/SMB 同长度远端 A→B 更新；
- 不同端口/账号/编码是否确实生成不同中转文件；
- 同服务器不同账号重新认证。

请确认：

- 在无服务器环境下，N01 应标记“实现完成/网络待验”，还是需要提供 fake `URLConnection`/SMB 抽象测试才算通过？
- 如果要做 fake 测试，推荐在哪一层抽象 `FilesTransferGateway` 的连接创建？
- 是否需要申请测试服务器/设备，以及由谁提供？请不要在 md 中写入任何真实凭据。

---

## 5. N05A / N05B 会话引用统一（需要高阶设计批准）

> 这部分目前完全未实现，等待批准契约。

### Q5.1 是否确认使用已有 `folder_sessions.sourceType/networkConfigId`？

当前 Room v4 已有：

- `FolderSessionEntity.sourceType: SourceType`
- `FolderSessionEntity.networkConfigId: String?`

但生产目前实际使用 `AppSessionSourceStore`（SharedPreferences）。

请确认：

1. 是否批准将生产引用统一到这两个 Room 字段？
2. `AppSessionSourceStore` 应作为只读 legacy 适配器，还是保留双写一段时间？
3. 回填屏障放在哪里最安全：`HtmlReaderApp.onCreate`、`FilesRuntime.ensure`，还是 `FilesStartupHandler` 首次进入前？

### Q5.2 回填状态模型

建议但待批准：

- `sourceType = LOCAL` 且 `networkConfigId == null`：未绑定本地；
- `sourceType = SMB/FTP` 且 `networkConfigId` 为正 Long 十进制字符串：有效网络绑定；
- `sourceType = SMB/FTP` 但 ID 为空/非法/配置不存在：Unresolved；
- legacy 无法推断协议：sourceType = UNKNOWN。

请确认：

1. 这个状态模型是否足够表达现有 UI/导航逻辑？
2. Unresolved 时 `files` 页应显示什么状态、是否允许用户重新选择配置？
3. `FolderSessionDeleter`、`FoldersOverviewFragment`、`FilesStartupHandler` 是否都必须接入新的 binding 仓库？

### Q5.3 事务边界

- 创建网络会话并绑定：Room 会话 insert + networkConfigId update 是否必须在同一 Room 事务？
- 删除网络配置时清理会话绑定：需要跨 `NetworkConfigDao` 和 `FolderSessionDao` 的事务；当前 Repository 没有跨仓库事务上下文。
- 导入结束时更新会话绑定（N05B）必须在导入事务内完成。

请给出一套可执行的事务边界方案：

- 是否需要新的 `SessionBindingRepository`？
- 是否通过 `CoreRuntime.database.withTransaction` 在应用层组合？
- 仓储方法是否要移除 `withContext(IO)` 以避免破坏 Room 事务线程约束？

### Q5.4 N05B 身份匹配与 append 导入

建议身份元组：

```text
protocol + host + port + username + defaultPath + encoding
```

请确认：

1. `encoding` 是否必须纳入身份？ blank encoding 是否先归一化为 `Auto`？
2. `username` 大小写、host 大小写如何处理？
3. 导入后出现多个同身份配置时，是否全部标记 Unresolved，不选第一条？
4. `clearBeforeImport = false` 时，已有绑定何时保留、何时重绑、何时失效？
5. 配置 ID 改变但身份一致时，会话的 currentPath/sortOption/lastAccess 必须保持，对吗？

---

## 6. T12 迁移实现细节

### Q6.1 `@VisibleForTesting` 重载是否可接受？

当前 `NetworkConfigRepository` 有：

```kotlin
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
suspend fun migrateLegacyPlaintextIfNeeded(
    isEncrypted: (String) -> Boolean,
    encrypt: suspend (String) -> String
)
```

生产无参重载委托给它。App 测试用 fake lambda 验证 CAS。

请判断：

- 保留这个测试重载是否可接受？
- 还是建议引入 `PasswordCipher` 接口 / fake cipher 注入？
- 如果保留，是否需要标注“仅测试用途”并防止其他生产代码调用？

### Q6.2 分页偏移与并发

迁移用 `getAll(limit=100, offset=n)` 分页。迁移过程中若发生删除/新增，offset 可能漂移。

请确认：

- 是否改为 keyset 分页（`WHERE id > lastId ORDER BY id LIMIT n`）更稳妥？
- 当前每行异常 catch + 继续是否足够？
- CAS 返回 0 后不重试，是否正确？

---

## 7. T08 名称安全的边界

### Q7.1 exists + rename 不覆盖的承认范围

当前本地实现：

- `newFile.exists()` 时返回 Conflict；
- 不使用平台级 no-replace 原子 API；
- 注释承认不是跨进程原子。

请确认：

- 在 App 内单写锁语义下，这是否足够通过 T08？
- 是否需要进一步引入 Android `Files.move` / `ATOMIC_MOVE` / `NOFOLLOW_LINKS` 具体能力验证？

### Q7.2 SMB 名称限制

当前 SMB 拒绝：

```text
: * ? " < > |
```

但仍允许：

- 前导/尾随空格或点；
- 某些 SMB 服务器可能拒绝的 Unicode 控制格式字符。

请确认是否需要更严格的 SMB 名称规范，以及是否应把 `toName` 长度上限也做成共享校验。

### Q7.3 SMB 重命名冲突错误类型

当前目标存在时抛出：

```kotlin
throw FilesTransferException.Unknown(IllegalStateException("Target already exists"))
```

请判断：

- 是否应新增 `FilesTransferException.Conflict` 并映射本地化文案？
- 当前 exists 检查与 rename 之间的竞态是否需要额外说明？

---

## 8. N02 规划器 API 与兼容语义

### Q8.1 Planner 公开性

为了让 App 测试使用访问计数，当前 `FavoriteImportPlanner`、`FavoriteImportNode`、`FavoriteImportPlan` 都是 public。

请判断：

- 是否接受 public planner API？
- 或建议改为 core-domain 内部类 + core-domain 测试模块？
- `visitCount` 作为公开诊断字段是否合适？

### Q8.2 断链/环挂根语义确认

当前策略：

- `id == null` FOLDER：挂根；
- 缺失父 ID 的整棵子树：全部平铺挂根；
- 自环/多节点环：全部平铺挂根；
- FILE：父映射存在就挂父，否则挂根；
- 有向无环可达部分：父先于子。

请确认这完全符合 review 的“旧兼容语义”，尤其是：

- 缺失父节点的子树是否应全部挂根，而不是把子树根挂根后再让后代挂到它下面？
- 是否需要为“兼容/更合理”二选一给出明确决策？

### Q8.3 Validation 循环取消

N02 计划还提到“校验函数按表拆分，循环可取消”。当前 `validateBundle` 仍是一次性方法，内部 `forEach` 没有 `ensureActive`。

请确认：

- 这是否算 N02 未闭环？
- 是否需要拆分并加取消检查，还是由导入循环的 ensureActive 覆盖即可？

---

## 9. 环境与验收路线

### Q9.1 无设备/无服务器的验收口径

当前环境：

- 无 adb 设备；
- 无 FTP/SMB 服务器；
- Robolectric 无真实 PdfRenderer / AndroidKeyStore。

请给出明确口径：

- 哪些任务可以仅凭单测与代码审查关闭？
- 哪些必须等待设备/服务器？
- 是否提供“fake seam 测试优先”的替代方案？

### Q9.2 下一步顺序

在 N04 完成后，剩余候选：

- N03 复核/设备专项；
- N05A/N05B（需批准）；
- T04 Web 进度；
- T09 缓存预算；
- T10 缓存 lease；
- T11 网络身份/URI；
- T13A 日志/存图资源；
- T14 schema/migration；
- T15 append；
- T16 休眠用例。

请基于当前风险给出下一批 1–3 个推荐任务及验收方式。

### Q9.3 请复核我们已提交的实现

请重点复核以下提交是否引入回归：

```text
5e0d052  N01 下载复用/错误类型
80c578a  N02 收藏导入规划
1ef08a0  T08 名称校验/重命名
d896904  T12 凭据 CAS
c7bb7ea  T07 传输取消/超时
f3c31fd  N03 PDF 退出/回收
4b20bc7  N04 备份取消
```

尤其请关注：

- `ReaderRuntime.closePdfControllerAfter` 是否可能死锁或无限增长；
- `ReaderFragment.pdfExitJob` 与 `pdfContentState` 的生成代次是否一致；
- `LocalFileSystem.rename` canonical 判断的异常路径；
- `BackupStreamIo` 的取消检查是否与 `use` 关闭顺序冲突；
- `FilesTransferGateway` 去掉 `runCatching` 后异常映射是否完整；
- `FavoriteImportPlanner` 的公开 API 是否合理；
- `NetworkConfigRepository` 的测试重载是否会被误用。

---

## 10. 请在回答中提供的交付物

请在回复 md 中给出：

1. 每个 Q 的明确答案 / 决策；
2. 需要执行方修改的最小 diff 清单；
3. 需要新增的测试类型与 fake seam 设计；
4. 对 N03/N05A/N05B 的批准或驳回结论；
5. 下一批 1–3 个任务的执行顺序、允许文件、验收标准；
6. 哪些任务在当前无设备/无服务器环境下可以正式关闭，哪些必须保持待验收。

---

## 11. 禁止事项提醒

执行方在等待答复期间不会：

- 恢复不可靠的按长度下载复用；
- 降低认证要求；
- 删除用户真实文件或会话；
- 自动启用 Hilt；
- 恢复本地标题提取；
- 读取 `bundle_*.txt` 或 `apply_bundle.py`；
- 在日志或 md 中写入真实凭据。
