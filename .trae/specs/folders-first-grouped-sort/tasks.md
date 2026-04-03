# Tasks

- [x] Task 1: 重构文件列表排序为“目录组优先”
  - [x] SubTask 1.1: 在文件排序入口先按 isDirectory 分组
  - [x] SubTask 1.2: 保证目录组始终排在文件组之前

- [x] Task 2: 实现组内统一排序规则
  - [x] SubTask 2.1: 在目录组内应用当前排序字段与方向
  - [x] SubTask 2.2: 在文件组内应用当前排序字段与方向
  - [x] SubTask 2.3: 覆盖名称、时间、大小等已有排序类型

- [x] Task 3: 回归验证排序行为
  - [x] SubTask 3.1: 验证名称升/降序下目录与文件均分组有序
  - [x] SubTask 3.2: 验证时间、大小排序下目录仍置前
  - [x] SubTask 3.3: 执行编译检查确保无回归

# Task Dependencies
- Task 2 depends on Task 1
- Task 3 depends on Task 1, Task 2
