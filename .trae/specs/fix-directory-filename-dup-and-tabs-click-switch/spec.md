# 目录文件名去重与 Tabs 单击切换 Spec

## Why

- 目录浏览中文件名出现重复展示，影响可读性（即使已能正确解码，也被重复显示成两份名称）。
- Tabs 页面切换成本偏高：当前需要长按条目 → 弹出菜单 → 点击 Switch 才能切换，期望单击即切换并移除 Switch 按钮。

## What Changes

- 目录列表（Files 页面）
  - 调整列表项文本拼装，保证“主显示位只出现一次文件名”；第二行仅显示尺寸和时间等元信息；当标题与文件名相同时不再重复显示。
- Tabs 页面
  - 将条目“单击”行为改为直接切换到该文件（激活对应 Tab，并返回阅读器界面）。
  - 移除“Switch”按钮/菜单项及其相关代码。

## Impact

- 受影响规格：目录文件列表显示、Tabs 切换交互
- 受影响代码（参考）
  - Files 列表适配/绑定（如 FilesFragment 列表适配器 getView 文本组织）
  - Tabs 列表页面（TabsOverviewFragment）点击事件与导航逻辑
  - 相关菜单/按钮资源（移除 Switch）

## ADDED Requirements

### Requirement: 目录文件名仅显示一次
系统 SHALL 在目录列表中仅显示一次文件名；标题与文件名相同或被判定为不可靠时，不应重复展示在副标题位置。

#### Scenario: 文件名与标题相同
- WHEN 列表渲染某文件，且标题提取结果与文件名相同
- THEN 第一行显示文件名一次，第二行不再重复显示标题，仅显示元信息（大小/时间）

### Requirement: Tabs 单击直接切换
系统 SHALL 在 Tabs 列表中，单击任意文件条目即切换到该文件对应的阅读标签，并返回阅读界面。

#### Scenario: 单击切换
- WHEN 用户在 Tabs 页面单击某条目
- THEN 激活对应 Tab 并跳转到阅读器（ReaderFragment），不需要长按与 Switch 菜单

## MODIFIED Requirements

### Requirement: Tabs 上下文菜单
旧：通过长按条目并点击“Switch”切换  
新：单击即切换，保留“Close”“Close All”“Add to Favorites”等，其余不变

## REMOVED Requirements

### Requirement: “Switch”按钮/菜单
**Reason**: 单击即切换后，该按钮冗余、增加操作成本  
**Migration**: 删除 UI 项与相关逻辑；单击行为即为原“Switch”效果
