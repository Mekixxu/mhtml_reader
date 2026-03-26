# Storage Source Label Display Spec

## Why
目前在 Folders（文件浏览）页面中，当前目录路径的前缀标签显示存在逻辑错误：浏览 SD 卡或内部存储（LOCAL）目录时，由于前缀获取逻辑不严谨，有时会错误地显示为“FTP”或其他网络源标签。用户期望在不同类型的存储介质下看到清晰、准确的前缀标签。

## What Changes
- 修改 `FilesFragment.kt` 中的 `buildCurrentDirText` 函数。
- 强制依据当前的 `browseSource` 枚举和实际路径特征（如是否包含 SD 卡特有路径结构）来动态决定前缀标签。
- 规范化前缀输出：
  - FTP 目录显示为 `[FTP]`
  - SMB 目录显示为 `[SMB]`
  - 外部 SD 卡目录显示为 `[SD]`
  - 内部存储目录显示为 `[LOCAL]`

## Impact
- Affected specs: 文件夹路径状态显示
- Affected code: `FilesFragment.kt` 中的 `buildCurrentDirText` 及相关存储类型判定逻辑。

## ADDED Requirements
### Requirement: 准确的存储源标签
系统 SHALL 在 Folders 页面的当前路径文本中，使用正确反映当前存储介质类型的标签前缀。

#### Scenario: 浏览各种源
- **WHEN** 用户打开 FTP 目录
- **THEN** 路径前缀为 `[FTP]`
- **WHEN** 用户打开 SMB 目录
- **THEN** 路径前缀为 `[SMB]`
- **WHEN** 用户打开通过 SAF 获取的 SD 卡目录
- **THEN** 路径前缀为 `[SD]`
- **WHEN** 用户打开内部存储目录
- **THEN** 路径前缀为 `[LOCAL]`

## MODIFIED Requirements
无。

## REMOVED Requirements
无。