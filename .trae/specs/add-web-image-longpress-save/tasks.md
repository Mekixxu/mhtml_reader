# Tasks

- [x] Task 1: 接入 WebView 图片长按识别
  - [x] SubTask 1.1: 在 Reader 页面挂载长按监听并识别图片命中类型
  - [x] SubTask 1.2: 对图片命中弹出“保存图片”菜单

- [x] Task 2: 实现图片保存流程
  - [x] SubTask 2.1: 支持 http/https 图片下载并保存
  - [x] SubTask 2.2: 支持 data URL 图片解码并保存
  - [x] SubTask 2.3: 补充成功/失败反馈文案

- [x] Task 3: 交互与容错处理
  - [x] SubTask 3.1: 非图片命中保持现有行为不变
  - [x] SubTask 3.2: 异常场景下保证不崩溃并提示失败

- [x] Task 4: 回归验证
  - [x] SubTask 4.1: 验证普通图片与 GIF 长按都可触发保存
  - [x] SubTask 4.2: 验证保存成功后文件可见
  - [x] SubTask 4.3: 验证失败场景有提示且浏览功能无回归

# Task Dependencies
- Task 2 depends on Task 1
- Task 3 depends on Task 2
- Task 4 depends on Task 2, Task 3
