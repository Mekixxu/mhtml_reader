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
| B | T02 Q4 缓存完整性 | 待执行 | — |
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

待执行。

## 4. 批次 C（T03：Q2/Q3 缓存协调）

待执行。

## 5. 批次 D（T04：总回归与交接）

待执行。
