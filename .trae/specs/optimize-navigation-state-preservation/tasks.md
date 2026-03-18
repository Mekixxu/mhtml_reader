# Tasks

- [x] Task 1: Refactor MainActivity Navigation
  - [x] SubTask 1.1: Implement `switchFragment(tag: String, create: () -> Fragment, forceReplace: Boolean)` method in `MainActivity`.
  - [x] SubTask 1.2: Update `showContent` and `showOverview` to use `switchFragment`.
  - [x] SubTask 1.3: Update `showHomeRoot` to use `switchFragment` (while preserving backstack clearing).
  - [x] SubTask 1.4: Update `showDirectoryMode` and `showReaderMode` calls to use `forceReplace = false`.
  - [x] SubTask 1.5: Update `showDirectoryModeWithPath` and similar "Open" actions to use `forceReplace = true`.
