# APK Naming and App Icon Spec

## Why
目前打包出来的 APK 名字默认为 `app-debug.apk`，且使用的是 Android 系统默认的绿色机器人图标。为了正式发布到 Google Play，我们需要让打包产物具有明确的应用名称与版本标识，并替换为具有应用辨识度（MHTML/PDF 阅读器相关）的定制应用图标。

## What Changes
- 修改 `app/build.gradle.kts`，自定义 APK 输出名称，格式如：`MHTMLReader_v1.0.0.apk`（根据实际版本号和名称动态生成）。
- 移除默认的 `ic_launcher`。
- 使用 Vector Drawable 或图片生成一套符合应用主题（蓝色系）和功能（阅读、文档、网页档案）的启动图标（`ic_launcher` 和 `ic_launcher_round`）。

## Impact
- Affected specs: 应用打包配置、应用启动图标
- Affected code: `app/build.gradle.kts`、`app/src/main/res/mipmap-*` 及 `app/src/main/res/drawable/` 下的 launcher icon 文件、`AndroidManifest.xml`

## ADDED Requirements
### Requirement: 自定义 APK 命名
系统 SHALL 在执行打包（如 `assembleRelease` 或 `assembleDebug`）时，将输出的 apk 文件命名为包含应用名和版本号的格式。

#### Scenario: 执行 Build
- **WHEN** 运行 Gradle build task
- **THEN** 输出目录中的 apk 名称为 `MHTMLReader_v{versionName}_{buildType}.apk`

### Requirement: 自定义应用图标
系统 SHALL 使用具有文档阅读器特征的自定义图标作为桌面启动图标。

#### Scenario: 安装应用后查看桌面
- **WHEN** 应用安装到设备
- **THEN** 桌面显示新的自定义图标，而不是系统默认机器人
- **AND** 图标支持 Adaptive Icon（自适应图标）规范

## MODIFIED Requirements
无。

## REMOVED Requirements
无。