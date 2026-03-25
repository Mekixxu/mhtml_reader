# Tasks

- [x] Task 1: 编写 Gradle 脚本自定义 APK 输出名称
  - [x] SubTask 1.1: 在 `app/build.gradle.kts` 的 android 配置中增加 `applicationVariants.all` 逻辑。
  - [x] SubTask 1.2: 设定命名规则为 `MHTMLReader_v{versionName}_{buildType}.apk`。

- [x] Task 2: 清理并替换默认启动图标
  - [x] SubTask 2.1: 删除现有的系统默认 `ic_launcher` 相关文件（mipmap 下的内容）。
  - [x] SubTask 2.2: 使用 SVG/Vector 编写一个代表“网页/文档阅读器”的图标（例如结合文档形状和放大镜/书签元素，采用主题蓝色）。
  - [x] SubTask 2.3: 配置 Adaptive Icon（`ic_launcher.xml` 和 `ic_launcher_round.xml`），分别定义 background（纯色或渐变）和 foreground（前景色图标）。

- [x] Task 3: 构建验证
  - [x] SubTask 3.1: 执行 `assembleDebug`。
  - [x] SubTask 3.2: 检查输出目录，确认生成的文件名符合新规则。
  - [x] SubTask 3.3: 检查编译过程中没有关于 launcher icon 的资源错误。

# Task Dependencies
- Task 3 depends on Task 1, Task 2