# Tasks

- [x] Task 1: 改造 Tabs 列表项布局为“标题 + 右侧X关闭”
  - [x] SubTask 1.1: 新增或调整列表项布局，右侧提供明确可点击的 X 控件
  - [x] SubTask 1.2: 保持当前选中态与文本信息展示不退化

- [x] Task 2: 接入行内 X 关闭逻辑
  - [x] SubTask 2.1: 为每行 X 绑定对应 tabId
  - [x] SubTask 2.2: 点击 X 调用关闭单标签逻辑并刷新状态文案
  - [x] SubTask 2.3: 处理关闭当前选中项后的选中态回退

- [x] Task 3: 调整长按菜单项
  - [x] SubTask 3.1: 移除长按菜单中的 Close
  - [x] SubTask 3.2: 保留 Close All 和 Add to Favorites
  - [x] SubTask 3.3: 清理不再使用的分支与常量

- [x] Task 4: 回归验证 Tabs 交互
  - [x] SubTask 4.1: 验证行内 X 可关闭目标项且不卡顿
  - [x] SubTask 4.2: 验证长按菜单仅含 Close All / Add to Favorites
  - [x] SubTask 4.3: 验证单击切换标签与返回阅读器行为不变

# Task Dependencies
- Task 2 depends on Task 1
- Task 3 depends on Task 2
- Task 4 depends on Task 2, Task 3
