# mhtml_reader 审查与修改执行计划

> 面向较低阶 LLM；本文件是执行说明，不代表已实施修复。
> 编写基线：`221e71e`（应用 `1.0.44 / 45`，本文 F/T 问题行号仍对应此基线）。
> 当前复核基线：`1ef08a0`（应用 `1.0.58 / 59`；N01/R1+R2、N02/R6、T08/F09 已完成，N03 实现完成待复核）；最新状态与下一轮入口见 [2026-10-12 复核及下一阶段计划](review_next_plan_2026-10-12.md)。本文历史执行记录保留，但不等同于复核验收。
> 数据库：v4。
> 方法：项目文档核对、实际编译/装配路径追踪、关键生产源码与已有测试静态审查。不是逐文件形式化审计。
> 已阅读：`CLAUDE.md`、`PROJECT_INDEX.md`、`spec.md`、`ui_spec.md`、最近复核报告 `reviews/opencode_2026-09-26.md`。
> 没有分析 `bundle_*.txt` 或 `apply_bundle.py`；没有修改功能代码。

## 0. 给执行 LLM 的硬性指令

1. 先读 `CLAUDE.md` 和本文件，再读取当前任务涉及的源码及测试。以函数名定位，行号仅对应审查基线。
2. 每轮只实施一个任务卡。先报告目标、涉及文件和测试，再编辑；禁止顺便重构其他模块。
3. 用 Kotlin 1.9.24 / Java 17 / View + XML；不升级 Kotlin、AGP、Room 或 SDK，不引入 Compose。
4. 不删除现有功能、不清空真实文件、不清空用户数据库、不自动启用 Hilt，不恢复已经主动停用的本地标题提取。
5. 模块源文件在 `core/`，不是复制到模块目录。修改前检查对应 `build.gradle.kts` 的 `sourceSets`。
6. 普通函数体不超过 40 行，Fragment 函数体不超过 60 行；需要拆分时使用明确职责的小函数。
7. `CancellationException` 必须重抛；不得用 `catch(Throwable)`、`runCatching` 或 `Result.failure` 把取消包装成普通业务失败。
8. 数据库结构变更必须有迁移；禁止新增 destructive migration。测试使用临时目录/内存库/合成凭据，不能操作用户真实数据。
9. 功能性批次交付时递增 `versionName` 最后一位和 `versionCode`，生成命名正确的 Debug APK；纯文档不改版本、不生成 APK。从当前版本读取并递增，禁止硬编码本文基线。
10. 每个任务完成后执行测试、检查 diff、仅提交本任务文件，提交消息 `fix: <简述>` / `test: <简述>` / `docs: <简述>`。用户已有改动不得回退或混入提交。
11. 示例代码是局部重构方向，不是完整可直接替换的文件。必须保留现有错误模型、安全拦截、阅读布局 fallback 和会话位置恢复。
12. 标记“需复核”的任务：低阶模型可以补测试和提出最小 diff，必须由较高阶模型或人工复核后交付。遇到无法确定的状态所有权或数据库兼容性，停止该任务，不自行设计大范围架构。

### 0.1 实际编译归属

| 模块 | 生效源码 |
|---|---|
| app | `app/src/main/java`、`core/work` |
| core-base | `core/common`、`core/vfs/model` |
| core-storage | `core-storage/src/main/java`、`core/vfs/impl`、`core/vfs/local` |
| core-data | `core/database`、`core/data/repo`、`core/security`、`core/session/dao`、`core/session/entity` |
| core-domain | `core/cache`、`core/domain`、`core/title`、`core/backup`、`core/di`、`core/network`、`core/settings`、`core/maintenance` |
| feature-files | `core/fileops`、`core/files`、`core/favorites`、`core/session/repo`、`core/session/di` |
| feature-reader | `core/reader` |

唯一生效的 VFS 接口是 `core-storage/src/main/java/core/vfs/IFileSystem.kt`。运行时沿 `HtmlReaderApp`、`CoreRuntime`、`FilesRuntime`、`ReaderRuntime`，不是库内 Hilt binding。

## 第一阶段：架构评估

### 1.1 概览

多模块软拆分，Room 仓库管理元数据，Runtime 单例手工装配依赖。文件页按扩展函数/辅助类拆分；远程 FTP/SMB 网关主要在 app 模块，先下载中转文件，再由阅读模块复制到统一缓存。阅读页复用一个 WebView 和一个 PDF controller，标签模型与当前标签 ID 通过两个 StateFlow 发布。

### 1.2 主要优点

- 数据库有 `1→2→3→4` 迁移链，历史打开使用原子 UPSERT 保留进度，备份 UI 生效路径已经使用 Room 事务。
- 收藏写入口脱敏、网络密码 Keystore AES-GCM 加密、备份网络密码置空、Manifest 禁止系统备份，已有实质性安全防线。
- WebView 默认关闭 JavaScript，缓存 file URL 已做 canonical 路径校验；MHTML 布局 fallback 保留了实际阅读体验。
- 已有 JVM/Robolectric 回归测试，远程列表有 token 防旧结果覆盖，文件操作已加入互斥与忙碌提示。

### 1.3 核心缺点

- 单例装配与未生效 Hilt 路径并存；“已编译”不等于“被调用”，旧文档容易驱动模型修改错误入口。
- 阅读页异步任务缺少统一内容代次/所有权，PDF 打开、渲染、关闭可交错；标签状态、缓存保护和历史更新也不是一个原子操作。
- 网络阻塞 I/O 的取消、超时和资源清理不完整；缓存按当前落盘大小腾空间，没有完整在途容量预留。
- 规格和现状有差距，备份 DTO 没有覆盖目录收藏/编码/会话引用等语义；缺少迁移测试和 PDF 真机回归证据。

### 1.4 已核实的历史状态，不再重复修复

- 最近报告批次 E 的主要代码已存在：缓存失败后终止、WebView canonical 白名单、在途 key 保护、忙碌提示、收藏仓库脱敏/启动清洗、导入事务、错误日志部分脱敏。
- `1e97f30` 明确停用本地列表标题提取，保留标题模块类。旧规格“所有列表异步显示标题”不是自动恢复授权。
- 目录会话劫持、会话滚动位置与删除活跃会话修复已存在；必须保留对应测试。
- 现有 `CacheOpenManagerInFlightTest` 测的是 A 已写入后 B 无空间，不覆盖两个新拷贝在首块落盘前同时获得空间。

## 第二阶段：问题清单

严重程度指影响，P0/P1/P2 指执行顺序。以下是源码可证实的缺陷或契约缺口；并发触发概率、设备内存峰值尚未实测。

### F01 高：本地目录旧加载覆盖新会话

- 类型/维度：状态竞态；架构、性能。
- 位置：`app/src/main/java/html_reader/FilesFragmentLocalBrowse.kt:25-55`，`loadEntries()`。
- 机制：每次本地加载都新建 Job，IO 返回后无 source/session/path/token 校验。A 目录慢返回可覆盖 B 目录或 FTP 列表，并错误启用本地按钮。远程有 token，本地没有。
- 前：`launch { val entries = withContext(IO) { list(dir) }; allEntries.addAll(entries) }`
- 后：

```kotlin
val request = captureBrowseRequest()
localLoadJob?.cancel()
localLoadJob = viewLifecycleOwner.lifecycleScope.launch {
    val entries = withContext(Dispatchers.IO) { listLocal(request.directory) }
    if (!isCurrentBrowseRequest(request)) return@launch
    applyLocalEntries(entries)
}
```

### F02 高：PDF 打开/渲染/关闭并发与资源所有权不明确

- 类型/维度：资源竞态、生命周期；架构、性能。
- 位置：`core/reader/pdf/impl/AndroidPdfReaderController.kt:21-74`；`ReaderFragment.kt:332-370,485-501,715-728`。
- 机制：IO dispatcher 不是串行锁。一个任务渲染 Page 时另一个 `open()` 可关闭旧 renderer。构造 `PdfRenderer` 失败会遗留 descriptor；`page.render()` 失败遗留 Bitmap；销毁异步 lambda 读取可变 controller 字段，重建视图后可能关闭新实例。PDF→Web 时旧 renderer 未及时释放。
- 前：`renderer = PdfRenderer(pdfFileDescriptor!!)`；独立 `withContext(io)` 操作。
- 后方向：

```kotlin
private val mutex = Mutex()

override suspend fun open(file: File) = withContext(dispatcherProvider.io) {
    mutex.withLock {
        closeInternal()
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            renderer = PdfRenderer(descriptor)
            pdfFileDescriptor = descriptor
        } catch (error: Exception) {
            descriptor.close()
            throw error
        }
    }
}
```

其他 controller 操作必须使用同一锁；不要在锁内调用会再次取锁的 public 方法。关闭应对 renderer/descriptor 分别清理且最终置空。销毁前捕获 `val controllerToClose = pdfReaderController`，由显式所有者保证完成清理。

### F03 高：PDF 页状态漏渲染、旧结果污染新标签

- 类型/维度：状态管理、未捕获异常；架构、Kotlin。
- 位置：`ReaderFragment.kt:186-197,354-369,485-501`。
- 机制：`MutableStateFlow(0)` 对相同页码不重发。首次打开默认第 0 页时可能没有渲染触发；从 A 的第 0 页切到 B 的第 0 页尤其容易保留旧图。打开/渲染恢复后没有 tab/代次检查，且 page collect 渲染异常无业务边界，可能终止收集或崩溃。PDF 打开 catch 也吞取消。
- 前：仅依赖 `observePageChanges()` 触发；渲染后直接 `setImageBitmap`。
- 后方向：

```kotlin
val generation = contentGeneration
val tabId = tab.tabId
val bitmap = controller.renderPage(pageIndex, width)
if (generation != contentGeneration || tabId != selectedTabId) {
    bitmap.recycle()
    return
}
pdfPreviewImage.setImageBitmap(bitmap)
```

打开完成必须显式保证初始页渲染，不能依赖相同整数 StateFlow 事件；用内容代次区分同页码不同文件。读取/保存进度前后均检查所属标签。

### F04 中：Web 阅读进度只保存不恢复，停止跟踪丢最后一笔

- 类型/维度：数据一致性；架构。
- 位置：`DefaultReaderTabManager.kt:122-136`、`ReaderFragment.kt:397-449`、`WebViewProgressTracker.kt:24-46`。
- 机制：tab 中有 `lastKnownProgress`，阅读页未使用它滚动恢复；`stopTracking()` 取消 500ms debounce，不落最后位置。切换复用标签时 tab 的初始位置也不会自动更新为最新历史值。
- 前：`job?.cancel()`；页面完成仅做布局 fallback。
- 后方向：

```kotlin
val range = (webView.contentHeight * webView.scale - webView.height).coerceAtLeast(0f)
webView.scrollTo(0, (range * progress.coerceIn(0f, 1f)).toInt())
```

在布局/fallback 稳定后、当前 URL 和 tab 匹配时恢复一次；恢复期间暂停进度写入，不能把加载时的 0 覆盖原位置。停止时用捕获的 path/progress 做最终持久化，不读取切换后的 WebView。

### F05 高：备份 URI 读写在主线程，导入无大小上限

- 类型/维度：ANR、内存耗尽；性能、防御编程。
- 位置：`MoreFragment.kt:370-402`。
- 机制：`lifecycleScope.launch` 默认主线程，`ContentResolver` 读写和 `readText()` 同步执行。大 JSON/慢 SAF provider 可卡 UI，全量无界读入可耗尽堆。
- 前：`BufferedReader(...).readText()`。
- 后方向：

```kotlin
val appContext = requireContext().applicationContext
val text = withContext(Dispatchers.IO) {
    readUtf8WithByteLimit(appContext.contentResolver, uri, MAX_BACKUP_BYTES)
}
```

限实际读取字节，不只信 provider 长度；超限在清库前失败。上限是新产品限制，数值需确认并资源化提示。

### F06 高：目录收藏和 FTP 编码无法完整备份往返

- 类型/维度：元数据丢失；架构、维护性。
- 位置：`JsonBackupManager.kt:131-170,97-109`；`core/backup/model/ExportBundle.kt`；`FavoritesRepository.addDirectory()`。
- 机制：目录收藏与收藏文件夹均为 `FOLDER`；导入一律 `addFolder`，忽略目录收藏的 path/sourceType，目录快捷入口变空文件夹。`NetworkConfigDto` 不含 encoding，非 UTF-8 配置导入后回到 Auto。
- 前：`favoritesRepo.addFolder(parentId = newParentId, name = dto.name)`。
- 后方向：

```kotlin
if (dto.path.isNotBlank()) {
    favoritesRepo.addDirectory(parentId, dto.name, dto.path, fromSafeSourceType(dto.sourceType))
} else {
    favoritesRepo.addFolder(parentId, dto.name)
}
```

这是局部示意；必须与原有 idMap、目录层级顺序一致。DTO 新增 `encoding: String = "Auto"` 保持旧 JSON 可读；不能偷偷更改 schemaVersion 拒绝所有旧备份。
- 状态（2026-10-11 T06A）：✅ 已修复，详见 5.2.6；未改网络会话 ID 策略。

### F07 高：替换网络配置后存量会话绑定失效，导入输入校验不足

- 类型/维度：引用完整性、取消语义；架构、安全。
- 位置：`JsonBackupManager.kt:55-126,190-196`；`AppSessionSourceStore.kt`；`FilesFragmentSession.kt:67-88`。
- 机制：导入清空 network_configs 后用 `id=0` 新建，旧目录会话通过 SharedPreferences 引用旧配置 ID。可能成为悬空绑定；ID 重用时可能指向错误服务器。Room 事务不覆盖 prefs。导入前仅校验 schemaVersion，非法枚举/进度/重复 id 未严格处理；`catch(Exception)` 将取消变成 `Result.failure`。
- 前：清库后逐项解析和插入，默认 `BackupTransactionRunner.Direct` 可静默非事务。
- 后方向：

```kotlin
val bundle = decodeBundle(text)
validateBundle(bundle)
try {
    transactionRunner.run { importValidatedBundle(bundle) }
} catch (cancelled: CancellationException) {
    throw cancelled
}
```

必须另外设计“旧配置身份→新配置 ID→旧会话”的映射/不可达语义，并由高阶复核；不能以清空全部会话解决。旧备份密码导入是否保留应维持兼容，不擅自删除。
- 状态（2026-10-12 T06B 第一阶段）：schema/枚举/范围/ID/长度校验与 CE 重抛已修复；会话引用映射待方案复核，详见 5.2.7。

### F08 中：FTP/SMB 传输阻塞、取消被重新包装

- 类型/维度：取消/超时契约；Kotlin、性能。
- 位置：`FilesTransferGateway.kt:26-114,132-149`；`FilesRemoteController.kt:250-253`。
- 机制：`copyTo` 不检查协程取消，FTP `openStream()` 未显式设置连接/读取超时；`mapTransferError` 把 CE 变 Unknown。外层重抛 CE 不足以保证底层循环停下。取消过程中远程上传可能已产生部分目标，必须说明而非报成功。
- 前：`input.copyTo(output, BUFFER_SIZE)`；`return FilesTransferException.Unknown(error)`。
- 后方向：

```kotlin
if (error is CancellationException) return error

while (true) {
    currentCoroutineContext().ensureActive()
    val count = input.read(buffer)
    if (count < 0) break
    output.write(buffer, 0, count)
}
```

阻塞 read 期间靠超时/关闭资源退出，不能声称 `ensureActive()` 会即时打断 socket。

### F09 高：重命名可覆盖目标，输入可越过父目录

- 类型/维度：数据完整性、输入校验；安全。
- 位置：`LocalFileSystem.kt:204-213`；`FilesFragmentEntryDialogs.kt:19-40,150-176`；`FilesSmbGateway.renameEntry()`。
- 机制：UI 只限制非空，新名允许 `../x`、分隔符或控制字符；`File(parent,toName)` 可以越过父目录。Android/Linux `renameTo` 对既有文件可能覆盖。当前有 UI 入口，不是休眠 Move 用例问题。
- 前：`if (oldFile.renameTo(newFile)) ...`。
- 后方向：

```kotlin
validateSingleEntryName(toName)
val target = File(parent, toName)
if (target.canonicalFile.parentFile != parent.canonicalFile) return failure()
if (oldFile.canonicalFile == target.canonicalFile) return success(from)
if (target.exists()) return nameConflict()
```

存在检查仍有 TOCTOU，不能宣称对外部进程原子 no-replace。UI、VFS、SMB 变更入口共同校验；仅串行化应用自身写操作无法阻止其他应用/服务器客户端。

### F10 高：缓存上限不是并发硬上限，删除失败被计为释放

- 类型/维度：空间预算、竞态；性能、防御编程。
- 位置：`CacheEvictor.kt:49-66`；`CacheOpenManager.kt:58-110`。
- 机制：两个新 key 在落盘前分别检查空间，都可 READY；保护 key 只防删除，不预留各自尚未写入字节。删除返回值被忽略后直接 `total -= item.size`，会误报空间足够。未知大小按 0 检查，实际可无界增长。
- 前：`item.file.deleteRecursively(); total -= item.size`。
- 后方向：

```kotlin
if (!item.file.deleteRecursively()) continue
total = measureCurrentUsage()
```

容量预留需包含“磁盘实际使用 + 在途剩余预留”，在同一锁内取得/释放 reservation，失败/取消均释放。不要简单相加完整文件大小与已落盘部分而双重计数。

### F11 高：标签发布与缓存保护交接存在窗口

- 类型/维度：状态原子性、资源所有权；架构。
- 位置：`CacheOpenManager.kt:130-133`；`DefaultReaderTabManager.kt:139-171,180-204`；`TabCacheRegistry.kt:16-32`。
- 机制：copy flow 完成即移除 inFlight，稍后才 bind；这期间新淘汰可删除待建 tab 缓存。tab/current ID 先发布后 bind，关闭可先找不到绑定，再被旧打开任务绑定，造成孤儿。关闭某个 tab 总是删除缓存，registry 没有共享 key 引用计数。`copyLocks` 永不移除，随不同版本/路径增长。
- 前：发布 tab → 切 current ID → bind → Ready → recordOpen。
- 后方向：

```text
取得缓存 lease → 确保历史记录可更新 → 在状态锁内绑定并发布标签 → Ready
取消/重复标签/失败 → 释放未转移 lease
关闭标签 → 移除状态与引用 → 最后引用释放后删除缓存
```

不能直接 `copyLocks.remove(key)`：等待旧 Mutex 的任务与新建 Mutex 会并发写同 key。需要引用计数锁条目或有界锁池，并补等待者竞争测试。

### F12 中：收藏匹配配置忽略账号，URI 解码语义错误

- 类型/维度：身份选择、输入处理；安全、Kotlin。
- 位置：`FilesNetworkOpenResolver.kt:36-59`。
- 机制：先只按协议/host/port 选配置，URL 显式用户名可能被忽略，错误账户被用于访问。`URLDecoder` 是表单解码，会把 URI 路径的 `+` 当空格；userInfo 解码异常也无 Result 边界。
- 前：`URLDecoder.decode(uri.encodedPath, "UTF-8")`。
- 后方向：

```kotlin
val path = uri.path.orEmpty().ifBlank { "/" }
val candidates = configs.filter { matchesEndpoint(it, uri) }
val existing = selectByExplicitUsernameOrUniqueCandidate(candidates, username)
```

同 endpoint 多账号且 URL 不带账号时要明确提示选择，不采用列表第一项。解密失败配置不得被选择。

### F13 高：启动凭据迁移全行更新可覆盖用户刚保存的配置

- 类型/维度：读改写竞态；架构、安全。
- 位置：`NetworkConfigRepository.kt:65-80`；`HtmlReaderApp.kt:72-77`。
- 机制：后台启动迁移读取实体，再 `dao.update(entity.copy(password=...))` 覆盖全行。同期用户编辑 name/path/password 后，旧快照可写回并恢复旧密码。
- 前：`dao.update(entity.copy(password = credentialCipher.encrypt(stored)))`。
- 后方向：

```kotlin
@Query("""
    UPDATE network_configs SET password = :encrypted
    WHERE id = :id AND password = :expectedStored
""")
suspend fun migratePasswordIfUnchanged(id: Long, expectedStored: String, encrypted: String): Int
```

返回 0 表示已被并发修改，不重试覆盖；无需数据库版本变更。损坏的 `enc:v1:` 内容不能随意当明文重新加密来掩盖故障。

### F14 中：日志及存图旁路安全边界不一致

- 类型/维度：敏感日志、资源清理；安全、性能。
- 位置：`BlockingResourceWebViewClient.kt:40,71`；`ReaderFragment.kt:581-603,604-633`。
- 机制：资源/导航日志直接记录文档给出的 URL，可含明文 userInfo 或巨大 data URL。存图 HttpURLConnection 未 finally disconnect；非 base64 的限制按字符不按最终字节；图片长按可用主动 HTTP 请求越过 MHTML“禁外链加载”策略。
- 前：`Log.d(tag, "... url=$url")`；连接无 finally。
- 后方向：

```kotlin
try {
    return readImage(connection)
} finally {
    connection.disconnect()
}
```

日志优先只记 scheme/host/原因，不记录完整 data URL。存图属于用户主动行为是否允许网络请求，需要用户决策；本轮不能擅自删掉存图。

### F15 中：数据库缺少导出 schema 和升级保真测试

- 类型/维度：维护性、防御编程。
- 位置：`AppDatabase.kt:25`；`core-data/build.gradle.kts`；`CoreRuntime.kt:24-36`。
- 机制：`exportSchema=false`；现有 HistoryDaoTest 使用内存新库，不能证明旧库迁移保留数据。DEBUG destructive fallback 能掩盖遗漏迁移。
- 前：`exportSchema = false`。
- 后方向：

```kotlin
@Database(/* 保持现有 entities/version */, exportSchema = true)
// 在 core-data 的 Room compiler 参数中设置 schemaLocation。
```

没有历史 schema 不能伪造当时版本；根据真实迁移和历史源码构造旧库 fixture，再验证表/索引/默认值和数据保留。

### F16 低：本地 append 参数被忽略

- 类型/维度：接口契约；Kotlin、维护性。
- 位置：`LocalFileSystem.kt:42-49`。
- 机制：`File.outputStream()` 总是截断，`append=true` 也丢旧内容。目前未证实用户入口传 true，作为公共能力契约修复。
- 前：`File(path.filePath).outputStream()`。
- 后：`FileOutputStream(File(path.filePath), append)`。

### F17 低（接线后升高）：休眠用例含 SAF 假路径和标题并发问题

- 类型/维度：潜伏缺陷；架构、性能。
- 位置：`FileNameUtils.kt:19`、`NameConflictResolver.kt:31-40`、`ObserveDisplayTitlesUseCase.kt:45-145`。
- 机制：SAF childPath 返回父 URI，AUTO_RENAME 永远冲突；exists 失败当 false。标题 current map 被不同 dispatcher 并发修改、trySend 可丢更新、每项创建协程且完整复制文件才限量提标题。当前 Move/Copy 无 app 构造入口，本地标题已停用，不能按活跃性能瓶颈处理。
- 前：`VfsPath.SafTree(dir.uri)`；`exists(target).getOrDefault(false)`。
- 后方向：真正 provider 子项定位或明确不支持，错误上抛；标题状态单一消费者/代次、受控 worker 队列、仅必要字节提取。
- 禁止为“消除休眠代码”直接删除这些能力。只有确认重新接线才执行对应重构。

### F18 中：当前自动测试基线不通过（T00B 已修复）

- 类型/维度：测试平台保真、隔离与生命周期；维护性。
- 位置：`app/src/test/resources/robolectric.properties`、`HistoryDaoTest`、`DefaultReaderTabManagerTest`、`FolderPositionNavigationTest`、`TabsOverviewSelectionTest`。
- 本轮证据：app 84 项中 7 项失败；3 项在 `ShadowLegacySQLiteConnection` 报 `ON CONFLICT ... DO UPDATE` 的 `near "ON": syntax error`；另 4 项报 `Illegal connection pointer`。
- 机制：当前配置仅固定 sdk/Application，没有明确 SQLite mode；Robolectric 4.14.1 在 Windows 上默认 LEGACY，后端不支持 Room UPSERT。T00B 在 Linux 强制 LEGACY 后完整复现 7 项失败；4 项连接指针用例单独运行也失败，因此根因是 LEGACY 后端的连接/线程模型，不是跨测试污染；显式 NATIVE 后恢复。
- 前：仅 `sdk=35`、`application=android.app.Application`。
- 后方向：已在 `app/src/test/resources/robolectric.properties` 显式 `sqliteMode=NATIVE`（见 5.2.1）；ActivityController 成对清理 / Runtime fixture 清理属于后续测试卫生改进，本轮未证明是 F18 的必要条件。
- 禁止删除 UPSERT 原子更新或改回 get+REPLACE 来兼容测试的旧 SQLite。
- 状态（2026-10-11 T00B）：✅ 已修复，详见 5.2.1；未删除 UPSERT SQL，未弱化进度保留断言。

## 第三阶段：风险矩阵

修复成本：S 约半天内；M 约 1-2 天；L 多次迭代。指熟悉 Android 的工程师，不是低阶模型的时限承诺。

| ID | 问题 | 维度 | 严重度 | 顺序 | 成本 |
|---|---|---|---|---|---|
| F01 | 本地目录旧结果覆盖新页面 | 架构/性能 | 高 | P0 | M |
| F02 | PDF 并发与资源关闭 | 架构/性能 | 高 | P0 | L |
| F03 | PDF 首屏/切换漏渲染 | 架构/Kotlin | 高 | P0 | L |
| F04 | Web 恢复缺失/最终位置丢失 | 架构 | 中 | P1 | M |
| F05 | 主线程无界备份 I/O | 性能/安全 | 高 | P0 | M |
| F06 | 目录收藏/编码往返丢失 | 架构/维护性 | 高 | P0 | M |
| F07 | 导入后会话悬空/输入边界 | 架构/安全 | 高 | P0 | L |
| F08 | 传输阻塞/取消包装 | Kotlin/性能 | 中 | P1 | M |
| F09 | 重命名覆盖/路径越界 | 安全 | 高 | P0 | M |
| F10 | 并发缓存超限/假释放 | 性能/安全 | 高 | P0 | L |
| F11 | 缓存保护交接/标签发布竞态 | 架构 | 高 | P0 | L |
| F12 | 账号选择/URI 解码错误 | 安全/Kotlin | 中 | P1 | M |
| F13 | 凭据迁移覆盖并发编辑 | 架构/安全 | 高 | P0 | M |
| F14 | 日志及存图边界 | 安全/性能 | 中 | P1 | M |
| F15 | schema/迁移测试不足 | 维护性/安全 | 中 | P1 | M |
| F16 | append 失效 | Kotlin/维护性 | 低 | P1 | S |
| F17 | 未接线用例潜伏缺陷 | 架构/性能 | 低 | P2 | L |
| F18 | 当前测试后端/隔离失败 | 维护性 | 中 | P0 前置 | M |

## 第四阶段：可执行路线图

### 4.1 顺序与依赖

建议顺序：T00 → T00B → T01 → T02 → T03 → T05 → T06A → T06B → T08 → T12 → T09 → T10 → T04 → T07 → T11 → T13 → T14 → T15。

T03 依赖 T02；T06B 依赖 T05/T06A；T10 依赖 T09。其他任务可以独立实施，但低阶 LLM 每轮只做一项。T16 和 P2 决策项暂不进入执行。

### T00：先同步文档与基线（纯文档）

- 允许文件：`CLAUDE.md`、`PROJECT_INDEX.md`、`spec.md`、`ui_spec.md`、本计划状态表。
- 修改步骤：修正 VFS 接口路径；Hilt “已编译但运行时未接线”统一口径；移除本地浏览职责中的“标题刷新”描述；规格注明标题行为已由 `1e97f30` 覆盖，保留历史需求作为旧要求，不恢复代码。
- 把 Windows Java 17 配置与 Linux 示例分开；写明实测 `C:\Program Files\Java\jdk-17`，不要让 Windows 执行者设置 `/usr/lib/jvm/...`。
- 保留真实待定功能：WEB/文内搜索/PDF 缩放目录/Move Copy/可配置维护参数，不把“未实现”写成“已支持”。
- 验收：每个入口路径存在，模块映射与 Gradle 一致，当前版本从构建文件读取。文档提交，不构建 APK。

### T00B：恢复可信自动测试基线（F18，P0 前置）

- 允许文件：测试配置、失败测试、必要的 Runtime 测试隔离接缝；不改历史 UPSERT SQL。
- 步骤 A：单独运行 `HistoryDaoTest`，记录 SQLite 后端/版本；优先明确受支持的 native mode，不只提高 SDK 号；Windows 不支持所选 native 后端时使用可运行的 Linux/设备测试环境并记录限制，不声称本机通过。
- 步骤 B：单独运行 FolderPositionNavigationTest/TabsOverviewSelectionTest，再跑整套，区分独立失败与跨测试污染；成对清理 Activity 生命周期/协程/数据库，避免测试继续读取 Robolectric 已重置的连接。
- 静态单例重置必须先等待测试任务/资源关闭；禁止反射置空正在使用的数据库来掩盖生命周期缺陷。若需要生产 reset 接口，由高阶复核其可见性及仅测试用途。
- 自动验收：HistoryDaoTest 两个 touchOpen 用例在真实可用 SQLite 后端通过，保留进度测试不弱化；其余 4 项独立与全套均通过；连续运行全套两次无随机连接失效。
- 测试配置/测试代码改动不改变应用行为，不递增应用版本、不生成 APK；若必须改生产生命周期，独立为功能批次交付。

### T01：本地浏览代次与过期结果防护（F01，P0）

- 允许文件：`FilesFragment.kt`、`FilesFragmentLocalBrowse.kt`、必要的 `files/` 小型请求状态类、`FilesFragmentSession.kt`、对应测试。
- 步骤：保存 localLoadJob 与共享 browse generation；每次 source/session/path 切换先使旧请求失效；捕获请求快照，IO 完成后核验；销毁取消任务；异常只对当前请求展示。
- 不要只加 Job.cancel 而不校验代次，不改变排序/过滤/会话滚动存储。
- 自动验收：A 慢 B 快最终只显示 B；本地 A→FTP 后 A 不覆盖远端列表；同路径不同会话不串位置；销毁后无 UI 更新。
- 手工：大目录快速进入/返回、切本地/FTP/SMB，路径标签、列表、按钮始终同源。

### T02：PDF controller 串行与可靠关闭（F02，P0，需复核）

- 允许文件：`AndroidPdfReaderController.kt`、必要的 controller 测试 seam、`ReaderFragment.kt` 的销毁捕获/所有者接入、测试。
- 步骤：统一 Mutex；renderer 构造失败关闭未转移 descriptor；渲染失败回收新 Bitmap；关闭两类资源并置空；销毁捕获旧实例，清理 Job 不误关新 controller。
- 使用明确 application/内容所有者的清理 scope 或受控关闭入口；不要为每次关闭留下未管理的独立 SupervisorJob；不得取消尚未完成的旧清理而不兜底。
- 自动验收：open/render/close 被串行；损坏 PDF 的 descriptor 不泄漏；重复 close 幂等；旧销毁任务仅关闭旧实例；渲染失败释放新 Bitmap。
- 设备验收：快速打开两个 PDF、旋转、离开/回来、PDF→MHTML，无 “current page open” 异常或 descriptor 增长。
- 停止条件：无法明确 renderer 是否拥有 descriptor 的关闭语义时，先核对目标 SDK API 文档，不用双重关闭假设替代验证。
- 状态（2026-10-11）：实现完成，待高阶复核与设备验收，详见 5.2.3。

### T03：PDF 初始渲染和内容代次（F03，P0，需复核）

- 允许文件：`ReaderFragment.kt`、必要的 `core/reader/` 内容状态辅助类、测试；不修改其他页面。
- 步骤：每次选择内容递增 generation；取消旧 load/render job；捕获 tab/controller/page；open 完成显式触发初始页渲染；相同页码不同 PDF 必须刷新。
- 对所有 PDF await 后 UI/历史副作用加代次检查；取消独立重抛，损坏文件错误受控展示；无标签时清理控件与旧图。
- 新标签历史先建立再保存初始位置，不能让 page=0 的过早事件覆盖已恢复的位置。进度公式建议 `pageIndex / max(1,pageCount-1)`；单页的百分比口径先确认，但 pageIndex 必须正确。
- 自动验收：首次第 0 页可见；A0→B0显示 B；A慢→B快只显示 B；关闭时晚到 Bitmap 回收；坏 PDF 不终止整个页面；保存进度属于渲染的 tab。
- 手工：第一页、恢复第 N 页、快速连续翻页、旋转、关闭正在加载标签。
- 必须保留 `mhtml_layout_fallback_reload` 行为，不趁此拆掉 WebView 兼容配置。
- 状态（2026-10-11）：实现完成，待高阶复核与设备验收，详见 5.2.4。

### T04：Web 恢复与最后进度持久化（F04，P1）

- 允许文件：`ReaderFragment.kt`、`WebViewProgressTracker.kt`、必要的进度持久化入口、测试。
- 步骤：加载前停止旧 tracker 并捕获最终位置；从当前历史读取最新进度；页面/布局 fallback 稳定后恢复一次；恢复完成再开启写入。
- 最后写入由有明确生命周期的所有者接收不可变 path/progress，不能向已销毁 view scope 提交期待执行的任务。
- 自动验收：50% 重开恢复；<500ms 快速退出也保存；程序恢复不先写 0；A→B 后 A 最终写入不覆盖 B；fallback 重载不重复恢复旧位置。
- 真机：短文/长文、缩放、旋转、布局 fallback、同标签往返。

### T05：备份 I/O 限量、离开页面与确认（F05，P0）

- 允许文件：`MoreFragment.kt`、新增 `app/.../backup/` 读取小类、字符串资源、测试；新增目录同步索引。
- 步骤：先捕获 applicationContext，URI 开关流与实际读写在 IO；按字节限量读取并确保关闭；导入/导出互斥并显示状态；覆盖导入前展示明确确认。
- 建议上限先提交提案，例如 20 MiB；用户未确认前只做可注入限量能力与测试，不擅自把合法旧备份排除。
- 自动验收：超限在任何 DAO 清库前失败；输入错误/取消流关闭；输出失败不提示成功；读取期间主线程可继续事件处理。
- 手工：慢 SAF provider、大 JSON、拒绝覆盖确认、操作时离开页面。
- 状态（2026-10-12）：✅ 已完成；用户确认上限 20 MiB 并已启用，详见 5.2.5。

### T06A：备份目录收藏与编码往返（F06，P0）

- 允许文件：`JsonBackupManager.kt`、`ExportBundle.kt`、必要的仓库恢复入口、`BackupImportTransactionTest.kt`/新增往返测试。
- 步骤：区分空 path 收藏容器与非空 path 目录收藏；所有 FOLDER 共用 oldId→newId 映射；保留 path/sourceType，适用时保留 createdAt；编码字段缺失默认 Auto。
- 自动验收：本地/SMB/FTP 目录收藏、嵌套收藏夹、文件收藏导出再导入语义相同；GBK 配置不回 Auto；旧 schema=1 备份可读；网络密码导出仍为空；任意失败仍回滚全部表。
- 不要在此改网络会话的 ID 策略，该项交给 T06B。
- 状态（2026-10-11）：已完成，详见 5.2.6。

### T06B：导入验证与网络会话引用策略（F07，P0，需复核）

- 允许文件：backup 模块、生效 `MoreFragment` 装配、`AppSessionSourceStore`/会话协调辅助类、相关 DAO/仓库、测试。任何 DB 结构变更另立 Migration。
- 第一步只补失败测试：旧配置 id=7 关联会话→导入同身份但新 ID；导入不含旧服务器；旧 ID 被其他服务器占用；导入事务失败。
- 第二步提出并复核映射方案：按明确的协议/host/port/username/defaultPath 身份匹配，保留会话本身和滚动位置；找不到身份时标记绑定不可用并提示，不降级到本地根目录、更不能错误连接其他服务器。
- Room 与 SharedPreferences 非原子。要么持久化待完成映射并支持崩溃恢复，要么在独立批准的迁移任务中统一引用存储；低阶模型不能凭 finally 写 prefs 宣称原子。
- 校验在清库前执行：枚举、端口、有限 progress 0..1、pageIndex≥-1、收藏 ID 唯一、父关系策略、记录数/字符串长度；不静默丢未知收藏类型。循环/断裂关系当前挂 root 的兼容行为是否保留需明确。
- `transactionRunner` 在生产构造时必须显式提供，避免 Direct 静默兜底；取消重抛；导出需要一致快照，避免并发编辑造成父子错配。
- 自动验收：关联仍指向正确服务器；缺失服务器可见失效状态；进程中断恢复一致；失败/取消不清库；旧密码兼容导入但不导出密码。
- 停止条件：没有批准的引用/崩溃恢复方案，不执行全量替换策略改写。
- 状态（2026-10-12）：第一阶段（导入校验）完成；会话引用映射待复核，详见 5.2.7。

### T07：传输取消、超时、临时文件（F08，P1）

- 允许文件：`FilesTransferGateway.kt`、`FilesRemoteController.kt` 的 FTP 列举、必要 `FilesSmbGateway.kt` 资源关闭、测试。
- 步骤：统一可取消流式 copy；mapTransferError 保留 CE；FTP 显式配置连接/读取超时；SMB 使用库支持的 timeout/close 语义，核对实际 API。
- 临时文件清理放 finally，成功改名前检查取消；不删除远程半成品以“回滚”，除非有已批准的上传事务策略；失败提示可能部分上传。
- 自动验收：中途取消停止后续块写入且抛 CE；普通 IO 异常仍映射；临时文件清理；失败时原下载目标未损坏；超时不永久占操作锁。
- 手工：服务器断网/不响应、大文件取消、离开页面；不得在日志打印带密码的 FTP URL。
- 新增取消按钮/传输任务中心属于产品扩展，另行批准，不与底层契约修复混做。

### T08：单项名称校验与重命名不覆盖（F09，P0）

- 允许文件：`FileNameUtils.kt` 或 `core/common` 单项名称校验器、`LocalFileSystem.kt`、新建/重命名用例、SMB 变更入口、UI 错误字符串、测试。
- 步骤：拒绝空、`.`、`..`、`/`、`\`、NUL/控制字符；按协议增加限制；本地验证父目录；同名重命名可幂等；既有目标明确失败并保留双方数据。
- App 内目标目录写操作共享锁；如果需要真正平台级 no-replace 操作，先确认 API 和跨挂载点语义，由高阶复核，不用 exists+rename 宣称消除所有竞态。
- 自动验收：重命名到既有文件双方字节不变；`../outside`/绝对路径失败；SMB 名称校验一致；普通中文/多点扩展名可用；CreateFolder 同样拒绝越界。
- 手工只用临时样本目录，不对真实文件尝试覆盖。
- 状态（2026-10-12）：✅ 已完成，commit `1ef08a0`，版本 `1.0.58 / 59`；本地重命名不覆盖、名称校验、SMB 入口校验与 UI 错误文案均已落地。

### T09：缓存真实释放与容量预留（F10，P0，需复核）

- 允许文件：`CacheEvictor.kt`、`CacheOpenManager.kt`、必要缓存预算类、测试。
- 步骤 A：删除失败不减预算，重算实际占用；明确等于上限是否可容纳，保持一致边界测试。
- 步骤 B：统一原子 reservation，多个拷贝共享预算；每写入块校验未知大小增长；成功/失败/取消释放预留；实际磁盘不足仍按 IOException 失败。
- 自动验收：空缓存 max=200、A=150/B=150 同时在首块前预约，最多一个获准；A=100/B=100 的边界由确认口径决定；未知长度超限停止；删除失败返回 CANNOT_FREE；活跃/在途不删。
- 不要把整个大文件 IO 放进全局 Mutex 来伪装并发预算；锁仅保护预算/必要目录变更。

### T10：缓存 lease 与标签原子发布（F11，P0，需复核）

- 允许文件：`CacheOpenManager.kt`、`TabCacheRegistry.kt`、`DefaultReaderTabManager.kt`、相关接口及测试。
- 步骤：先定义 lease 生命周期和唯一释放者，再实施保护从 inFlight 到 tab 的交接；新标签绑定完成后才发布；关闭/duplicate/取消释放对应引用；历史记录在 Ready 前完成或明确独立错误策略。
- 同 key 最后引用关闭才清目录；key 锁清理考虑等待者计数，禁止直接 remove。UI 在 Ready 中取消 collect 不得留下无历史的新标签。
- 自动验收：copy 完成但 tab 未建时淘汰不删；发布即关闭无孤儿绑定；相同请求并发最终一个 tab；多个 owner 释放不误删；锁等待者/新请求没有两把同 key Mutex；失败无泄漏。
- 不要把状态锁跨任意 suspend DAO/IO 调用持有；设计快照/事务边界后再复核。

### T11：网络收藏身份与 URI 解码（F12，P1）

- 允许文件：`FilesNetworkOpenResolver.kt`、调用方选择/错误处理、现有 resolver 测试、字符串资源。
- 步骤：先解析 username 再选配置；显式账号必须匹配；无账号仅唯一候选自动复用；多候选提示选择；拒绝 decryptFailed；URI 解码不把 `+` 转空格。
- 自动验收：alice/bob 同服务器选 bob；无账号多配置不任选；`a+b.mhtml` 保持 `+`，`%2B` 同义；非法 userInfo 不抛出未捕获异常；anonymous FTP 既有行为不回归。
- 不把 protocol+host+port 当完整身份，也不把用户名写进密码占位字段。

### T12：凭据启动迁移 CAS（F13，P0）

- 允许文件：`NetworkConfigDao.kt`、`NetworkConfigRepository.kt`、凭据迁移测试。
- 步骤：新增仅 password 列的 compare-and-set 查询；加密在 IO，CAS 返回 0 正常跳过；保留用户刚编辑其他字段；异常只记录类别/ID。
- 自动验收：迁移读旧密码后用户更新新密码→迁移不覆盖；只修改 name/path→迁移可以更新 password 而不覆盖其他列；重复迁移幂等；解密失败的存量密文仍按不可用呈现。
- 不改 schema/version；新增 DAO SQL 不需要 Migration，但功能交付仍按规则递增应用版本。

### T13：日志与存图资源清理（F14，P1）

- 允许文件：`BlockingResourceWebViewClient.kt`、存图辅助类或 `ReaderFragment.kt`、测试。
- 步骤 A：所有来自文档的 URL 日志去 userInfo、截断，不输出 data payload；HTTP 连接所有出口 finally disconnect；data URL 解码后再验最终 bytes；读循环响应取消。
- 自动验收：`ftp://user:secret@host` 日志无 secret；巨大 data URL 不落完整日志；异常/超限连接断开；多字节内容不越字节上限。
- 步骤 B（待用户确认）：MHTML 用户主动保存外链图是否允许，是否提示网络请求；不擅自关掉功能。HTTP 可能请求局域网主机，不得只凭“用户点击”宣称没有旁路风险。

### T14：Room schema 与迁移防线（F15，P1）

- 允许文件：`AppDatabase.kt`、`core-data/build.gradle.kts`、schema 文件、迁移测试及测试资源配置。
- 步骤：启用 schema export，并把路径接到真正编译 Room 的 core-data kapt；提交当前 v4 schema；旧库 fixture 按历史真实结构建立。
- 自动验收：1→4、2→4、3→4 保留收藏/历史/网络数据，encoding/sourceType/sort 默认正确，唯一索引仍存在；不调用 destructive fallback 的迁移测试通过。
- schema 版本不变；没有实体变化不要新增 4→5。是否移除已有 DEBUG fallback 另列明确决策，不顺手修改。
- Robolectric 不适合的 migration helper 路径放设备测试并实际运行，不能把只编译过写成验证通过。

### T15：append 契约（F16，P1）

- 允许文件：`LocalFileSystem.kt`、追加测试。
- 步骤：本地使用 `FileOutputStream(file, append)`；保留 SAF `"wa"/"w"`；补 import。
- 自动验收：原内容 A + append B 得 AB；非 append 得 B；不存在文件行为与接口约定一致。

### T16：休眠用例修复（F17，P2，暂不执行）

- 仅在批准 Move/Copy UI 或重新接线标题提取后启动。
- Move/Copy 接线前先解决 SAF 子项定位、exists 错误上抛、目录递归/copy+delete 跨卷语义、流式进度/取消/冲突。
- 标题提取接线前先解决 map 单消费者、列表代次、队列有界、结果背压及完整缓存复制成本。
- 不批准接线时只标注保留状态和测试缺口，不删除能力。

### 4.2 P2 架构演进与用户决策

| 决策 | 已核实现状 | 推荐后续动作 |
|---|---|---|
| 手工 Runtime 或 Hilt | 库未聚合，维护走 App workers | 先维持 Runtime；完整迁移单独立项并逐项回归 CLAUDE 1.4 |
| 维护参数可配置 | SettingsRepository 存在，生效路径主要默认常量 | 先明确产品是否要暴露；批准后 Runtime/Worker/启动清理读取同一设置源 |
| Move/Copy/FTP 文件变更 | Move/Copy 无 app 构造点，FTP 主要浏览/传输 | 确定范围，再执行 T16 前置修复和 UI，不宣称跨源能力已完成 |
| WEB 与文内搜索/PDF 缩放目录 | 规格要求，Reader 生效实现以本地 PDF/MHTML 为主 | 分功能立项；保持 MHTML 禁外链，不能为 WEB 开启全局 JavaScript |
| 本地标题 | 近期主动停用 | 同步规格，不自动恢复 |
| SAF 与所有文件权限 | Manifest 保留 MANAGE_EXTERNAL_STORAGE | 先确认分发渠道和目录授权目标，不直接删权限或改成全 SAF |
| FTP 凭据 URL | 当前 Java URL FTP 需要 userInfo，与严格“禁止 URL 字符串”规范冲突 | 记录仅内存传输例外；长期改显式认证的 FTP client，单独回归编码/匿名/大文件 |
| 下载复用 | 每次重新下载，确定性中转路径被替换 | 需要 server identity/账号/编码/mtime/size 校验，不能仅 exists 或相同大小复用旧文件 |
| 会话/标签状态 | Room + prefs 分散，ReaderRuntime 持有手工 ViewModel | 在 P0 稳定后再统一 owner/state snapshot；不直接 Hilt 化或删除 ViewModel |

## 5. 验证基线与命令

### 5.1 环境

- 已实测：Windows 11、Oracle Java 17.0.12、Gradle Wrapper 8.10。
- PowerShell 配置仅影响当前进程，不修改系统设置：

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat --version
.\gradlew.bat :core-base:testDebugUnitTest :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:lintDebug
```

- 不公开 `local.properties`、网络凭据或 keystore.properties。SDK 路径从本机配置核验。
- 功能批次输出：`app/build/outputs/apk/debug/MHTMLReader_v<实际版本>_debug.apk`。
- 设备测试需真实连接：`.\gradlew.bat :app:connectedDebugAndroidTest`。未连接不能报通过。

### 5.2 T00B 修复前验证记录

- `java -version`、`.\gradlew.bat --version`：通过。
- 执行 `.\gradlew.bat :core-base:testDebugUnitTest :app:testDebugUnitTest`：core-base 20/20 通过；app 84 项，77 通过、7 失败，整体 BUILD FAILED。历史报告的 99 例不是本轮结果。
- 3 项 UPSERT 语法失败：`DefaultReaderTabManagerTest.openNewTab_deduplicatesSamePathAndVersion`、`HistoryDaoTest.touchOpen_insertsNewRowWhenMissing`、`HistoryDaoTest.touchOpen_preservesExistingProgressAndPageIndex`。
- 4 项连接指针失败：`FolderPositionNavigationTest` 的 `scrollPosition_isRestoredAfterOpenFileAndReturn`、`reopenLocalFolderCard_resumesNestedDirectory`、`openFileThenReturnToFolders_restoresNestedDirectory`，以及 `TabsOverviewSelectionTest.tabsOverview_highlightsCurrentTabNotTopItem`。
- 原始本轮报告在 `app/build/reports/tests/testDebugUnitTest/index.html` 和 `app/build/test-results/testDebugUnitTest/`，它们是构建生成物，不提交、不作为永久交接依赖；错误已摘录到 F18/T00B。
- PDF/WebView 真机、网络服务器端到端、lint、APK：本轮未验证；本轮仅文档，无须 APK。

### 5.2.1 T00B 执行记录（2026-10-11 修复会话）

- 环境：Linux x86_64、JDK 17、Gradle 8.10、Robolectric 4.14.1、`sdk=35`（Robolectric 依赖使用本机缓存）。
- 根因复现：强制 `robolectric.sqliteMode=LEGACY`（Robolectric 4.14.1 在 Windows 的默认值）后，F18 的 7 项失败全部复现：3 项为 `ON CONFLICT ... DO UPDATE` 语法错误，4 项为 `Illegal connection pointer`。4 项连接指针用例单独运行同样失败，排除跨测试污染。
- 修复：`app/src/test/resources/robolectric.properties` 显式声明 `sqliteMode=NATIVE`，并注释说明原因。Robolectric 自带 native runtime 覆盖 Windows x86_64、Linux x86_64、macOS x86_64/aarch64。
- 验证：
  - `app` 92 项 + `core-base` 20 项 = 112 项，连续两次运行 0 失败。
  - 4 项连接指针用例在修复后单独运行通过。
  - 即使把测试 JVM 的默认值显式覆盖为 `LEGACY`，包级 `sqliteMode=NATIVE` 仍生效，全套 112 项通过。
- 结论：F18 关闭；未改动 Room UPSERT SQL，未删除或弱化测试；本轮为测试配置改动，不改变应用行为，不递增版本、不生成 APK。

### 5.2.2 T01 执行记录（2026-10-11 修复会话）

- 实现：新增 `FilesBrowseRequestGuard` / `LocalBrowseRequest`；`FilesFragment.loadEntries()` 每次调用先取消旧 `localLoadJob` 并递增代次，捕获 source/session/directory 快照；IO 返回后校验代次、来源、会话、目录，未通过则丢弃；`onDestroyView()` 取消未完成加载。
- 自动验收：`FilesBrowseRequestTest` 覆盖代次失效与 source/session/directory 匹配；`FilesLocalBrowseGenerationTest` 覆盖 A 慢 B 快最终只显示 B、本地 → FTP 后旧本地结果不覆盖远端模式。
- 验证：`app` 96 项 + `core-base` 20 项 = 116 项，0 失败；`:app:assembleDebug` 通过，产物 `MHTMLReader_v1.0.48_debug.apk`。
- 版本：`1.0.48 / 49`（功能批次按规则递增）。
- 手工验收待执行：大目录快速进入/返回、本地/FTP/SMB 切换时路径标签、列表和按钮保持同源。

### 5.2.3 T02 执行记录（2026-10-11 修复会话）

- 实现：`AndroidPdfReaderController` 使用单一 Mutex 串行化 open/render/goToPage/getPageCount/close；`PdfRenderer` 构造失败时显式关闭尚未转移所有权的 descriptor；`page.render` 失败时回收新 Bitmap；`closeInternal` 先置空字段，再在 finally 中分别关闭 renderer 与 descriptor。
- 生命周期：`ReaderFragment.onDestroyView()` 捕获当前 controller 实例，提交到 `ReaderRuntime.closePdfController()` 的应用级清理 scope，避免旧清理误关视图重建后的新 controller，也避免为每次销毁创建未管理的独立 SupervisorJob。
- 自动验收覆盖：`AndroidPdfReaderControllerTest` 验证重复 close 幂等、未 open 时 render 抛错、损坏 PDF open 失败后可继续 close；`ReaderRuntimePdfCleanupTest` 验证清理只作用于传入实例、单个 close 失败不影响后续清理。
- 验证：`app` 101 项 + `core-base` 20 项 = 121 项，0 失败；`:app:assembleDebug` 通过，产物 `MHTMLReader_v1.0.49_debug.apk`；版本 `1.0.49 / 50`。
- 未验证/需复核：Robolectric 无法生成可用 PdfDocument，因此缺少真实 PDF open/render 集成测试；设备验收（快速打开两个 PDF、旋转、离开/回来、PDF→MHTML、descriptor 是否增长）仍需人工或高阶复核。
- 复核重点：`ReaderRuntime.closePdfController` 应用级 scope 的生命周期是否可接受；Mutex 在长耗时的 page.render 期间是否会造成预期外的关闭等待。

### 5.2.4 T03 执行记录（2026-10-11 修复会话）

- 实现：新增 `PdfContentState` / `PdfRenderKey`；`ReaderFragment` 每次选择内容递增 generation 并取消旧 `pdfLoadJob` / `pdfRenderJob`；open 完成后显式触发初始页渲染，不再依赖 `MutableStateFlow(0)` 的重复发射；渲染键包含 tabId + pageIndex，因此 A0→B0 会重新渲染并丢弃 A 的晚到 Bitmap；旧代次、旧 tab、已切页、视图销毁后的异步结果统一回收，不写 UI / 进度。
- 回收：`AndroidPdfReaderController.renderPage` 在取消时先回收 bitmap 再抛 `CancellationException`，避免“render 完成但结果无人接收”的中间态泄漏。
- 自动验收：`PdfContentStateTest` 覆盖代次失效、pending/applied 去重与 force、A0→B0 必须重渲染；实际设备上的 A 慢 B 快、晚到 Bitmap、坏 PDF 不终止页面仍待验证。
- 验证：`app` 104 项 + `core-base` 20 项 = 124 项，0 失败；`:app:assembleDebug` 通过，产物 `MHTMLReader_v1.0.50_debug.apk`；版本 `1.0.50 / 51`。
- 未验证/需复核：Robolectric 无法生成真实 PDF 页面，本轮无法用单测覆盖 ReaderFragment 与真实 renderer 的端到端交互；需要真机验证“首次打开第 0 页、恢复第 N 页、快速连续翻页、旋转、关闭加载中的标签”。

### 5.2.5 T05 执行记录（2026-10-11 修复会话）

- 实现：新增 `BackupStreamIo`，按实际读取字节数限流，成功/失败/取消都会关闭输入流；`MoreFragment` 的导入/导出改为 `ContentResolver` 打开流后在 `Dispatchers.IO` 读写，导入前增加覆盖确认，并用 `backupOperationInProgress` 避免并发操作。
- 限额（2026-10-12 更新）：用户确认 20 MiB；`MoreFragment.maxBackupBytes` 已启用 `BackupStreamIo.PROPOSED_MAX_BYTES`。超限时在任何 DAO 清库前失败，文案为 `more_backup_too_large`。
- 自动验收：`BackupStreamIoTest` 覆盖正常读写、超限失败、按字节而非字符计数、超过缓冲区、写出失败仍关闭流；`app` 110 项 + `core-base` 20 项 = 130 项，0 失败。
- 验证：`:app:assembleDebug` 通过，产物 `MHTMLReader_v1.0.51_debug.apk`；版本 `1.0.51 / 52`。
- 手工验证：慢 SAF provider、大 JSON、拒绝覆盖确认、操作中离开页面的真机行为仍需人工验证。

### 5.2.6 T06A 执行记录（2026-10-11 修复会话）

- 实现：`JsonBackupManager.importFavorites` 对所有 FOLDER 统一使用 `restoreFavorite`；`path` 为空的 FOLDER 恢复为收藏夹容器，`path` 非空的 FOLDER 恢复为目录收藏，保留 `path` / `sourceType` / `createdAt`；文件收藏同样保留原字段。
- 仓库：`FavoritesRepository.restoreFavorite` 作为备份恢复入口，统一剥离 URL 密码，不做路径去重，避免破坏备份中的父子关系。
- 编码：`NetworkConfigDto` 新增 `encoding: String = "Auto"`；导出写入真实 encoding，导入缺省值回 Auto；旧 schema=1 JSON 可直接读取。
- 自动验收：`BackupRoundTripTest` 覆盖本地/SMB/FTP 目录收藏 + 嵌套文件收藏往返、GBK 配置往返、导出不包含明文密码、旧备份缺 encoding 默认 Auto；`BackupImportTransactionTest` 原有回滚测试继续通过。
- 验证：`app` 113 项 + `core-base` 20 项 = 133 项，0 失败；`:app:assembleDebug` 通过，产物 `MHTMLReader_v1.0.52_debug.apk`；版本 `1.0.52 / 53`。
- 边界：网络会话与新网络配置 ID 的引用映射仍留给 T06B，本轮未改动。

### 5.2.7 T06B 第一阶段执行记录（2026-10-12 修复会话）

- 已实现：`JsonBackupManager.importAll` 在清库前执行 `validateBundle`，覆盖 schemaVersion 之外的枚举、端口 1..65535、progress 0..1、pageIndex ≥ -1、收藏 ID 唯一性、parentId 合法性、单表记录数与字符串长度；非法备份不会触发任何 DAO 清空。
- 取消语义：`CancellationException` 不再被 `catch(Exception)` 包成 `Result.failure`，而是原样重抛。
- 自动验收：新增 `BackupImportValidationTest`，验证未知收藏类型/sourceType/fileType、未知网络协议、非法端口、非法进度与页码、重复收藏 ID 都在保存种子数据之后被拒绝；`app` 120 项 + `core-base` 20 项 = 140 项，0 失败。
- 验证：`:app:assembleDebug` 通过，产物 `MHTMLReader_v1.0.54_debug.apk`；版本 `1.0.54 / 55`。
- 待复核：会话引用映射方案。候选方向：
  1. 在 `AppSessionSourceStore` 中引入 network identity（protocol/host/port/username/defaultPath），导入前先同步写入 identity 键，读取时优先按 identity 匹配、legacy ID 兜底；DB 回滚/提交都可保持可解析，但需要高阶审查 prefs 写入失败与旧数据回填路径。
  2. 持久化 pending remap journal，事务提交后完成 ID→ID 映射，并支持启动恢复。
  3. 统一引用存储或彻底移除 network ID 依赖，作为独立迁移任务立项。
- 本轮未执行候选 1/2/3 的生产接线，符合 T06B 停止条件；需要高阶确认后再做。

### 5.3 每任务的完成标准

1. 先有能表达目标缺陷的测试或设备复现步骤，再改生产代码。
2. 任务新增测试与现有 `core-base`/`app` 单测通过；若环境失败，记录具体命令、错误和未验证部分，禁止跳过测试后标记完成。
3. 功能改动 Debug 构建通过；设备必验任务附设备/API/WebView 版本和结果。
4. diff 只含允许文件与必要索引/版本变化；无功能删减、无敏感信息、无 unrelated changes。
5. Git 提交完成，更新下表：commit、版本、测试、设备证据。后续任务按新源码重新读文件。

## 6. 执行状态与交接模板

| 任务 | 当前状态 | Commit/版本/验收 |
|---|---|---|
| T00 文档同步 | ✅ 已完成 | `1dac33b`；同步 `CLAUDE.md`/`PROJECT_INDEX.md`/`spec.md`/`ui_spec.md`/本计划；仅文档，无测试/APK；执行基线 `157724c` / `1.0.47(48)` |
| T00B 测试基线 | ✅ 已完成 | `27df5cc`；`sqliteMode=NATIVE`；app 92 + core-base 20 = 112 项连续两次通过；测试配置变更，无版本号/APK |
| T01 本地浏览代次 | ✅ 已完成 | `50063b6`；v1.0.48(49)；app 96 + core-base 20 = 116 项通过；APK `MHTMLReader_v1.0.48_debug.apk` |
| T02 PDF 所有权 | 部分修复/未验收 | `1677b52`；v1.0.49(50)；历史 121 项通过；2026-10-12 复核 R3 交接回收、R4 内容退出未闭合，执行新计划 N03；真实 PDF 与设备验收待补 |
| T03 PDF 内容代次 | 代次实现通过/整体未验收 | `08b7672`；v1.0.50(51)；历史 124 项通过；2026-10-12 复核确认显式首屏方向，R3 与真实渲染证据待 N03 补齐 |
| T04 Web 进度 | 待执行 | — |
| T05 备份 IO | IO/限额通过，取消未闭合 | `f0d617c` + `12234a2`；v1.0.53(54)；历史 133 项通过；20 MiB 已启用；2026-10-12 复核 R5 同步读写不检查取消，执行 N04 |
| T06A 备份往返 | ✅ 已完成 | `93e47ed`；v1.0.52(53)；app 113 + core-base 20 = 133 项通过；APK `MHTMLReader_v1.0.52_debug.apk` |
| T06B 会话绑定 | 基础校验通过/复杂度及映射未完成 | `85f414e`；v1.0.54(55)；本轮 Windows 重跑 app 120 + core-base 20 = 140 项全通过；R6 执行 N02，引用统一/事务映射按 N05A/N05B 分阶段复核 |
| T07 传输取消 | 待执行 | — |
| T08 重命名防护 | ✅ 已完成 | `1ef08a0`；v1.0.58(59)；app 136 + core-base 23 = 159 项通过；APK `MHTMLReader_v1.0.58_debug.apk`；SMB 实机覆盖场景待网络验收 |
| T09 缓存预算 | 待执行/需复核 | — |
| T10 缓存标签交接 | 待执行/需复核 | — |
| T11 网络身份 | 待执行 | — |
| T12 凭据 CAS | 待执行 | — |
| T13 日志存图 | 待执行/网络行为待确认 | — |
| T14 Room 迁移 | 待执行 | — |
| T15 append | 待执行 | — |
| T16 休眠用例 | 暂缓/需批准接线 | — |

给低阶 LLM 的单轮提示词：

2026-10-12 起先读取新计划，以 N01 为推荐下一轮；下方是旧任务通用模板，不能据历史完成记录跳过复核未闭合项。

```text
读取 CLAUDE.md 和 reviews/execution_plan_2026-10-11.md。
本轮只执行任务 Txx，不执行其他任务，也不恢复/删除未授权功能。
先确认最新 git 状态、源码编译归属及任务依赖是否完成。
读取任务允许文件与相关现有测试，先建立目标失败测试或设备复现步骤。
按任务要求实施最小修改，保留已有安全、布局与会话行为。
如果标注需复核，提交测试与最小 diff 给高阶模型确认；不自行扩大架构。
完成后报告修改文件、测试命令和结果、设备验证、剩余风险。
按仓库规则递增功能版本、构建 APK、提交本任务文件并更新执行状态。
若权限/方案/环境阻塞，明确说明，不将待验项写成通过。
```
