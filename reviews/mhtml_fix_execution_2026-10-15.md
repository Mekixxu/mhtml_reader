# MHTML 修复执行记录（`mhtml_fix_plan_2026-10-15.md`）

## 0. 基线与范围

- 计划来源：`reviews/mhtml_fix_plan_2026-10-15.md`；复核来源：`reviews/mhtml_current_review_2026-10-15.md`。
- 起始基线：`8617ad4`（计划文档），功能基线 `b2c8fe1`，版本 `1.0.71 / 72`。
- 仅处理 Q1-Q4。PDF、上传、备份/收藏契约、Hilt 启用均不在范围内。
- 未提交 V03（备份/网络凭据游标迁移/本地 rename 及测试）保持原样，未纳入本批提交。
- 构建使用 JDK 17。

## 1. 批次状态总览

| 批次 | 任务 | 状态 | 版本 / APK |
|---|---|---|---|
| A | T01 Q1 有效手势接管 | 已完成 | 1.0.72 / 73 |
| B | T02 Q4 缓存完整性 | 已完成 | 1.0.73 / 74 |
| C | T03 Q2/Q3 缓存协调 | 待执行 | — |
| D | T04 总回归与交接 | 待执行 | — |

## 2. 批次 A（T01：Q1 有效手势接管）

### 2.1 问题

旧实现把任意 `ACTION_MOVE` 当作主动接管：不校验 down 坐标、位移阈值、手势取消与内容尺寸，
轻触抖动即可把 `webRestoreInteractionGeneration` 置为当前代次，使恢复流程停止定位并放行采样，
`stopAndFlush` 随后把当前位置（仍为 0）尾写覆盖旧进度（history 0.6 → 0.0）。

### 2.2 变更

- 新增 `core/reader/web/WebGestureTakeoverState.kt`：平台无关状态机。
  - `onDown` 记录按下坐标与内容 generation；
  - `onMove` 仅当位移平方超过 `touchSlop²` 且内容尺寸有效时返回 true；
  - generation 不一致或尺寸无效时不解锁，但保留手势以便后续有效位移累计；
  - `clear` 在 ACTION_CANCEL / ACTION_UP 清理。
- `app/src/main/java/html_reader/ReaderFragment.kt`：
  - 触摸监听器抽取为 `handleWebPreviewTouch(event)`（生产路径），DOWN/MOVE/CANCEL/UP 分别处理；
  - `webTouchSlop` 由 `ViewConfiguration.get(context).scaledTouchSlop` 提供，不再使用固定 1px；
  - `markCurrentWebContentTakenOver()` 只在有效拖动时置位并放行采样；
  - 仍返回 `false`，不消费触摸事件（点击链接/缩放/长按不受影响）；
  - `loadSelectedWebTab`、`invalidateWebContent` 增加 `webGestureTakeover.clear()`。

### 2.3 回归

- `app/src/test/java/com/html_reader/WebGestureTakeoverStateTest.kt`：slop 阈值、无 DOWN、跨代次、尺寸无效延后、clear 六项。
- `app/src/test/java/com/html_reader/ReaderFragmentGestureTakeoverTest.kt`：通过反射取回 Fragment 安装的生产 `OnTouchListener` 驱动触摸序列，使用真实 Room/Writer/Tracker：
  - 轻触（<slop）后 progress 保持 0.6；
  - 有效拖动后接管并写入新位置 1.0，不因旧进度回拉；
  - 内容零尺寸时拖动不接管、不放行采样；
  - DOWN 后 CANCEL 再 MOVE 不接管。

### 2.4 测试统计

- `:core-base:testDebugUnitTest :app:testDebugUnitTest`：`app 240（2 跳过）+ core-base 23 = 263`，failures 0 / errors 0 / skipped 2。

### 2.5 交付

- 版本 `1.0.72 / 73`；APK：`app/build/outputs/apk/debug/MHTMLReader_v1.0.72_debug.apk`
  - SHA256 `A115C5BAD1F1325319A027FC7804B00A375436DD774C6C15A5787E75BEECCCC3`

### 2.6 设备待验（未执行）

- 真机轻触/抖动/长按图片/缩放不误覆盖或回拉；后台返回正常。
- Robolectric 不能替代真实 Chromium 触摸分发与 TalkBack 验证。

## 3. 批次 B（T02：Q4 缓存完整性）

### 3.1 问题

`CacheOpenManager.openToCache` 把 `totalBytes: Long` 当已知长度使用，且直接写入最终
`content.[ext]`；EOF 后无条件上报 `CopyProgress(totalBytes, totalBytes)`，从不比较实际
copied。TabManager 先采样本地文件长度、再打开输入流，源在打开前被截短或拷贝期间原地截短时
read 正常返回 EOF，于是缓存里只剩前 60 字节却上报 100/100 并发布 Ready 标签（Q4）。

### 3.2 变更

- `core/cache/model/CopyProgress.kt`：`totalBytes` 改为 `Long?`（`null` 未知、`0` 已知空文件）。
- `core/reader/model/OpenState.kt`：`Copying.totalBytes` 同步为 `Long?`。
- `core/cache/CacheOpenManager.kt`：
  - 签名 `totalBytes: Long?`；`generateCacheKey(size: Long?)` 与之一致（`versionStamp` 非空时键不变）。
  - 先写本次专属 `content.[ext].part`，关闭输出后再校验实际字节数；
  - 校验通过才 `delete + rename` 提交为最终成品；失败/取消只删除本次 `.part`；
  - 已知长度不相等即失败：截短报 `AppError.IoError("Incomplete copy: expected=… actual=…")`，
    增长（`copied + read > totalBytes`）抛 `EOFException("Source length changed while copying")`，两者都不会落入末尾假成功；
  - 每块写入前 `ensureActive()` 并做长度检查；
  - 上报真实 copied；缓存命中只命中已提交成品；Windows 下先关闭句柄再删临时文件（否则删除失败）；
  - CancellationException 直接重抛、取消态不再 emit；`flush/close` 失败走失败路径而非 finally 吞掉。
- `core/reader/tab/DefaultReaderTabManager.kt`：本地已存在文件取真实长度（0 为已知空文件），
  SAF/网络等未知长度传 `null`；建 Ready 前要求成品存在且已知长度时长度一致。
- `core/domain/usecase/ObserveDisplayTitlesUseCase.kt`：仅正数视为已知长度，其余按未知处理；
  `buildVersionStamp` 取值不变，避免标题缓存无谓失效。
- `app/src/main/java/html_reader/ReaderFragment.kt`：`Copying` 的 `totalBytes == null` 或 `<= 0` 时按不确定进度展示。

### 3.3 回归

- `app/src/test/java/com/html_reader/CacheOpenManagerIntegrityTest.kt`（7 项）：
  截短 100→60 报错且无半成品；100/100 成功并上报真实字节；增长 100→140 不假成功；
  已知空文件提交 0 字节成品；未知长度接受实际字节且 `totalBytes` 为 null；
  失败不损坏其他持有者的有效缓存；取消清理半成品且从不提交。
- `app/src/test/java/com/html_reader/DefaultReaderTabManagerTest.kt` 新增：
  真实 `LocalFileSystem` + TabManager 链路，打开输入流前原地截短到 60，
  断言无 Ready、有 Error、无 `content.*` 半成品、无 tab 建立（正式串联，而非仅断言 helper）。

### 3.4 测试统计

- `:core-base:testDebugUnitTest :app:testDebugUnitTest`：`tests=271 failures=0 errors=0 skipped=2`（较 T01 基线 263 新增 8 项）。

### 3.5 交付

- 版本 `1.0.73 / 74`；APK：`app/build/outputs/apk/debug/MHTMLReader_v1.0.73_debug.apk`
  - SHA256 `21C96A09D25DF1C7651683125CB79FE8F572FB250AD18BE7A03A91F160B95BA5`

### 3.6 未闭合 / 下一步

- 设备待验（未执行）：本地/SMB/FTP 打开 MHTML，拷贝期间中断源文件后应显示失败而非"完成"后空白；
  FTP/SMB 未知长度源（`totalBytes=null`）仍可正常打开。
- 未知长度源无法检测同长度内容变化（长度检查本身不证明 MHTML 语法有效），属计划已声明的边界。

## 4. 批次 C（T03：Q2/Q3 缓存协调）

待执行。

## 5. 批次 D（T04：总回归与交接）

待执行。
