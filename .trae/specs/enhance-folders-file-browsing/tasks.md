# Tasks

- [x] Task 1: 扩展排序能力并增加“大小升序”选项
  - [x] SubTask 1.1: 梳理现有排序入口与排序枚举
  - [x] SubTask 1.2: 增加“大小升序”并接入列表刷新
  - [x] SubTask 1.3: 验证目录项与文件项排序兼容性

- [x] Task 2: 增加文件名字体大小三档配置
  - [x] SubTask 2.1: 增加字体档位选项（小/中/大，默认中）
  - [x] SubTask 2.2: 将档位应用到列表文件名 TextView
  - [x] SubTask 2.3: 持久化档位并在页面初始化恢复

- [x] Task 3: 增加侧边快速滑动滑块
  - [x] SubTask 3.1: 选择并接入与 ListView 兼容的快速滚动方案
  - [x] SubTask 3.2: 支持拖拽按比例快速定位
  - [x] SubTask 3.3: 保持普通滚动手势行为不变

- [x] Task 4: 调整列表项信息密度与标记文案
  - [x] SubTask 4.1: 文件名主展示限制为最多两行并省略
  - [x] SubTask 4.2: 属性信息保持独立展示
  - [x] SubTask 4.3: 类型标记由 `[FILE]/[DIR]` 改为 `[F]/[D]`

- [x] Task 5: 回归验证浏览体验与稳定性
  - [x] SubTask 5.1: 大目录下验证排序、字体、快速滑动均可用
  - [x] SubTask 5.2: 验证超长文件名两行展示与属性行不冲突
  - [x] SubTask 5.3: 验证打开、选择、返回等现有行为无退化

# Task Dependencies
- Task 2 depends on Task 1
- Task 3 depends on Task 1
- Task 4 depends on Task 1
- Task 5 depends on Task 2, Task 3, Task 4
