# Tasks

- [x] Task 1: 修复路径前缀显示逻辑
  - [x] SubTask 1.1: 在 `FilesFragment.kt` 的 `buildCurrentDirText` 中，依据 `browseSource` 强制返回 `[FTP]` 和 `[SMB]`。
  - [x] SubTask 1.2: 当 `browseSource == BrowseSource.LOCAL` 时，通过 `currentSafTreeUri` 或路径特征判断是否为外部 SD 卡，若是则返回 `[SD]`，否则返回 `[LOCAL]`。

- [x] Task 2: 验证和构建
  - [x] SubTask 2.1: 执行 `assembleDebug` 确保无编译错误。
  - [x] SubTask 2.2: （若条件允许）验证不同存储类型的前缀是否符合预期。

# Task Dependencies
- Task 2 depends on Task 1