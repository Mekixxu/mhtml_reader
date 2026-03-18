# Tasks

- [x] Task 1: 定位 Favorites 列表项绑定逻辑并移除重复文件名拼装
  - [x] SubTask 1.1: 定位文件名显示来源与重复拼接位置
  - [x] SubTask 1.2: 修改为仅输出一次文件名

- [x] Task 2: 移除 Favorites 列表中的路径显示
  - [x] SubTask 2.1: 清理路径绑定与相关占位文案
  - [x] SubTask 2.2: 保证列表布局在无路径时仍对齐正常

- [x] Task 3: 回归验证 Favorites 基础交互
  - [x] SubTask 3.1: 验证打开收藏项行为不变
  - [x] SubTask 3.2: 验证重命名、删除等操作不受影响

# Task Dependencies
- Task 2 depends on Task 1
- Task 3 depends on Task 1 and Task 2
