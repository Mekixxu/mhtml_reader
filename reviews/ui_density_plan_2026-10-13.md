# Home 与文件浏览紧凑布局执行计划

> 用户已认可 1.0.62 的视觉风格，本轮只调整信息密度和布局，不重新配色。
> 编写基线：`5d862cc`，1.0.62 / 63。执行时必须核对最新 HEAD、版本和工作区。
> 本文为交接计划，尚未实施。目标页面是 Home 与 Folders 内的 FilesFragment，不是重做目录会话总览。
> 编写时存在其他执行者的 PDF 修改；不得暂存、提交、覆盖或回退这些改动。

## 1. 目标与固定决策

1. Home 缩小留白、最近/收藏并排、来源列表按实际内容占空间。
2. Browse files 压缩固定顶部，文件列表成为页面主要区域。
3. **批准把 Up 移至文件列表首个独立导航行**，移除原顶部 Up 的占位；保留功能与系统返回行为。
4. 保留蓝绿色 Material 3、浅深色、圆角、底栏四入口、所有网络/收藏/文件操作。
5. 不改数据库/网络实现、PDF/Web 生命周期、Hilt、标题提取、全局导航，不升级依赖，不引入 RecyclerView/Compose 重构。

当前拥挤原因：Home 卡片 padding20、纵向大按钮、SD固定112dp和网络固定172dp；Files 顶部叠加标题/搜索/两个Spinner/路径/操作/状态，多行固定占高。

## 2. 最终排布

### 2.1 Home

```text
紧凑标题 + 简短说明
[最近查看，主操作] [收藏，次操作]
来源卡片
  本地        下载入口
  SD/授权目录 实际入口；无卡仍保留授权入口
网络卡片
  标题 + 简短说明
  实际连接列表 + 新增连接
底部全局导航
```

- 页面水平 padding16dp；顶部8dp，卡片内部12dp，区块之间12dp。
- 主标题24sp，说明13sp，标题上边距0、说明下边距12dp，不再留24dp。
- 最近/收藏默认横向等宽，间距8dp，实际触摸高度≥48dp；图标20dp、图文间距8dp。
- 不保留一个单独的“快捷操作”标题占行；功能名仍清晰可读。这是用户授权的排布精简，不删除操作。
- 320dp窄屏或大字体/长翻译放不下时改纵向wrap_content，不用固定高度裁字。不要通过缩小字体到12sp以下来挤按钮。
- 来源卡片保留本地与SD分组，组内间距4–8dp；不能因为没有SD而删除授权目录入口。
- Home 行高最低48dp，内边距水平12dp；保留圆角表面，分隔4dp。
- 不全局减少 `home_card_padding/home_action_min_height`：新增仅Home/Files使用的密度dimen，避免更多/收藏等页面连带变化。

### 2.2 首页列表高度：禁止整页大范围填充占位

保留现有三个ListView及adapter/click行为；增加小型Home布局helper。

- 每次adapter内容更新、字体/宽度变化后按实际row测高，包含divider与padding；无实际项不保留多行空白。
- 本地默认一项仅占一行；SD实际1项不预留2行；网络一项新增入口不预留3行。
- 上限建议SD 3行、网络4行；超过上限保留列表内部滚动，不能隐藏多余配置/来源或取消滚动后使其不可达。
- 行测量必须使用布局完成后的真实可用宽度和主题字号；列表adapter变化后重新计算，不用 `count * 48dp` 截断大字体。
- 总布局仍由外部ScrollView滚动；限制列表内滚动区域只是控制密度，设备验证内外滚动手势。
- helper不持有Activity/Fragment静态引用，不重复注册无界layout监听，不每帧测所有项；仅测可见上限项并更新有变化的高度。
- 不改来源初始化、权限弹窗或配置存储；如观察到重复localDirs项，独立记录，不顺便重写Home业务。

### 2.3 Files 顶部：由多层变为紧凑三行

```text
搜索（可清空）                         [+ 操作]
当前位置单行摘要                       [显示选项]
进度/错误，仅有内容时显示
---------------------------------------------
↑ 上级目录            独立ListView导航header
目录 A
目录 B
文件 C
……
全局底栏
```

- “Browse files”大标题不再单独占行；页面身份由Folders底栏、路径和搜索提供。可作为搜索区无障碍描述保留。
- 搜索使用现有TextInputLayout与 `files_query_input`，保留clear_text和查询持久化；搜索输入正常触摸高度，不强缩TextInputLayout到无法显示hint。
- 新建/上传操作与搜索同排，触摸目标≥48dp。保留 `files_action_create` 及当前LOCAL/FTP/SMB文字和点击分流。
- 空间不足时用48dp图标动作，contentDescription随当前操作文字同步，按钮类型需与FilesUiBinder匹配；不写“新建”却实际FTP上传。
- 路径默认单行middle ellipsis，padding水平8/垂直6dp；可点击弹出完整路径文本（必须脱敏），方便确认位置；大字体允许wrap而不是截断无替代。
- 排序/字号移到“显示选项”弹层：**保留现有两个Spinner实例、监听、选项、设置恢复**，通过可展开/收起的独立容器实现最小改动。默认收起，按钮contentDescription与展开状态明确。
- 不拆成全新排序逻辑，不丢session.sortOption或字号prefs；布局中找到两个Spinner后原初始化可继续使用。
- 展开区临时占空间可接受，正常浏览收起；不要每次目录加载自动展开。
- `files_operation_status` 空/idle时GONE；进度/完成/错误有信息时可见。成功信息沿用原生命周期，不新增计时协程或擅自清除错误。
- 状态隐藏要从集中状态绑定同步处理，包含初始化idle路径；错误详情点击与进度功能保留。
- 根布局垂直padding8dp、列表marginTop4–8dp；ListView仍height0 + weight1，禁止放进ScrollView。
- 文件行最低56dp，上下padding8dp、divider4dp；文件名14/16/18sp选项与两行上限保留，元数据可见时自然增高，不固定高度裁切。

## 3. Up 导航行实现契约（强制）

### 3.1 最小方案：ListView header，不新增假BrowserEntry

- 新 `item_files_parent.xml` 为48dp最低高度导航行，有箭头、明确“上级目录”文本与Button触摸语义。
- 保留 `files_action_up` ID 与Button类型，改其布局位置。用 `addHeaderView(header, null, false)`，导航由header内Button处理。
- **必须在设置adapter之前添加header，仅添加一次。**
- `FilesUiBinder.bind(root)` 不能再默认从Fragment根查找尚未挂载的Up。明确改为 `bind(root, parentHeader)`（其余字段根查找、Up从header查找），初始化顺序：

```kotlin
val list = root.findViewById<ListView>(R.id.files_list)
val header = inflater.inflate(R.layout.item_files_parent, list, false)
list.addHeaderView(header, null, false)
val ui = FilesUiBinder.bind(root, header)
// 再装配adapter和原点击逻辑
```

- header始终存在且数量1。根目录显示禁用“已到顶层”或禁用Up，避免动态增删造成offset漂移；不可当成文件计数。
- 搜索/排序不影响header位置；空目录/无搜索结果时仍能向上导航，emptyLabel根据displayedEntries而不是含header的adapter.count。
- Button只调用现有navigateUp，不自行拼路径，不切会话/页面，不实现SMB/FTP新规则。
- 本地Up权限探测不得为header绘制在主线程新增 `listFiles()`。是否可向上采用廉价路径判断，真正点击仍走原navigateUp；无权限给原反馈。
- SMB/FTP根路径按现有helper约束显示禁用，未配置/加载/操作中的禁用状态沿原接线保持。不要仅依据本地currentDir判网络根。
- 不改变当前LOCAL能向上到的范围，不新增会话root限制或SAF能力；真实SAF边界若现有未支持需如实记录。

### 3.2 点击与长按偏移

当前FilesFragment使用 `displayedEntries.getOrNull(position)`；添加header后必须修改两个监听。

```kotlin
val item = parent.getItemAtPosition(position) as? BrowserEntry
    ?: return@OnItemClickListener
```

长按同样通过parent adapter取BrowserEntry，header返回null并消费/忽略，不弹文件操作、不选中。
禁止只修点击漏掉长按，禁止header position0打开真实第一文件。复查fast scroll、selection及所有getChildAt/position索引调用方。

### 3.3 滚动恢复与旧记录

当前FilesScrollStateStore保存原ListView position。新增固定header后旧position需要+1，不能让恢复错一行。

- 增加滚动记录schema版本或新key命名空间，不改Room。旧无版本记录恢复为oldPosition+1、保留top；新记录保存header-inclusive position（从0开始）并标版本。
- 新增记录不反复+1；空列表/超范围clamp，header位置0可保存恢复。
- 会话/path匹配要求保留；延迟post恢复仍检查view生命周期、当前session/path请求代次，不能旧目录post覆盖新目录。
- 测试旧记录迁移、新记录重复恢复、第一文件、header、空列表及快速切会话。

## 4. 执行批次与允许文件

### D01：首页紧凑布局

- 允许：fragment_home.xml、item_home_rect.xml、HomeFragment.kt（仅布局测高接线）、新增Home布局helper、UI密度dimen/文案、相关UI测试。
- 保留所有入口，先写一项SD/新增网络入口不留多行空白的测试，再调整。
- 宽屏/标准字体并排快捷操作；窄屏/大字体有fallback；不要修改shared theme/colors/其它页面的共享padding。
- 完成专项测试与截图后再进入D02，纯单测全绿不代表视觉密度达标。

### D02：Files紧凑顶栏与Up header

- 允许：fragment_files.xml、item_files_entry.xml、新增item_files_parent.xml、FilesFragment.kt、files/FilesUiBinder.kt、FilesScrollStateStore.kt、集中状态绑定及相关测试。
- 必要时增加Files布局/列表导航helper，保持普通函数≤40行、Fragment≤60行；不把新的导航逻辑塞到已有超长onViewCreated。
- 分两步验证：紧凑顶栏且Up暂未移动 → header接入/索引与滚动迁移。提交最终必须去掉原顶部Up占位。
- `setLocalActionButtonsEnabled`、FTP/SMB加载与操作禁用接线不许丢；新建/上传操作文案和contentDescription同步。
- 若必须改remote controller，仅允许UI状态映射，不触碰传输实现；超出范围先停下说明。

### D03：验证与交付

- 更新ModernUiThemeTest，保留浅深色、对比度、四项导航与原PDF布局断言；不要因新布局不便删回归。
- 新增Home密度测量、Files占高比例、header点击/长按/空态、滚动迁移、选项保留测试。
- UI测试measure必须dp转px，使用真实根可用高度，不能把360裸px当360dp。
- 功能修改按**执行时当前版本**递增，不硬编码1.0.63；按CLAUDE构建APK并提交本批文件。
- 同步ui_spec、CLAUDE目录索引/实际版本、PROJECT_INDEX和单独执行记录。不要把本计划改为“已完成”却缺证据。

## 5. 可量化验收

以360dp宽、640dp **内容区高度（扣系统栏和全局底栏）**、fontScale1.0、键盘隐藏、idle且选项收起为基准：

| 项目 | 验收目标 |
|---|---|
| Home快捷区 | 并排48dp最低触摸目标，标题到来源卡较旧版至少减少60dp，实际测量对比 |
| Home单项来源 | 实际一项不预留第二行空白；列表总height含padding能容纳整行 |
| Files固定顶部 | 建议≤160dp；未达目标必须说明真实字体/控件测量原因，不削触摸高度硬凑 |
| Files列表viewport | ≥内容区65%（含导航header）；实际文件区域扣header后≥55% |
| 标准文件行 | 常见单行名+元数据可看到至少5个完整条目，不把header算文件 |
| 窄屏/大字体 | 320dp、fontScale1.5/2.0所有操作可达，无文字裁切，不强制65%比例 |
| 横屏 | 列表仍可滚动，搜索焦点/键盘收起可恢复阅读区域 |
| 可访问性 | 所有可点触摸≥48dp，Up根态禁用语义明确，图标有准确动作描述 |

截图需同设备/主题/字体/数据集对比。目标之间冲突时优先可访问性和功能完整，其次密度。

必测路径：LOCAL子目录/根/无权限；FTP/SMB子目录/根/无配置；空目录；搜索无匹配；排序/字号后第一条点击与长按；网络慢旧请求；会话切换与旋转恢复；Up禁用时不能改变路径。服务器/设备不可用标待验，不虚构通过。

## 6. 命令、停止条件和提示词

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:testDebugUnitTest --tests "com.html_reader.ModernUiThemeTest"
.\gradlew.bat :core-base:testDebugUnitTest :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
# 设备连接后按需执行 connectedDebugAndroidTest，并提供前后截图
```

停止条件：需删除入口/改变协议和root边界、改Room、改PDF、跨模块大改、无法证明header索引/滚动一致性、与其他执行者直接冲突。不可用清理工作区来解决冲突。

```text
读取CLAUDE.md、ui_density_plan_2026-10-13.md、ui_refresh_2026-10-12.md。
本轮只执行D01；保留当前用户认可的配色，压缩Home留白，快捷操作并排并支持大字体fallback。
先核对git状态和当前版本，保护其他执行者PDF修改，只提交自己明确修改的文件。
用Home专用density dimen，不全局收紧更多/收藏；来源列表按实际行测高并保留超限滚动。
先建立密度/大字体失败测试，再实现，补真实截图或明确设备待验。
不要顺便执行V01-V03/N05或删除功能。后续D02必须同时修header索引和旧滚动记录。
记录测试计数、测量条件、APK、版本、截图/待验和commit；功能改动按当前版本递增。
```

本轮仅交付计划；不产生新版APK、不重跑业务测试、不触碰已有PDF工作区修改。UI执行不自动关闭此前业务审查问题。
