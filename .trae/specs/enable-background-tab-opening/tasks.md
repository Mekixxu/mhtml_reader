# Tasks
- [x] Task 1: Update OpenRequest and ReaderTabManager
  - [x] SubTask 1.1: Add `val background: Boolean = false` to `OpenRequest` data class in `core/reader/model/OpenRequest.kt`.
  - [x] SubTask 1.2: Modify `DefaultReaderTabManager.kt` to check `request.background`. If true, skip updating `_currentTabId` when a new tab is created or an existing one is reused.

- [x] Task 2: Update FilesFragment to support background opening
  - [x] SubTask 2.1: Inject `ReaderViewModel` into `FilesFragment` using `ReaderRuntime.viewModel()`.
  - [x] SubTask 2.2: Implement `openFileInBackground(file: File)` method in `FilesFragment` that calls `readerViewModel.open` with `background=true` and collects the result to show Toasts.
  - [x] SubTask 2.3: Update `openFtpFile` and `openSmbFile` signatures to accept `isBackground: Boolean`.
  - [x] SubTask 2.4: Update `onItemLongClickListener` for "Open in new tab" to call the appropriate open method with `isBackground = true`.
  - [x] SubTask 2.5: Ensure normal clicks still use foreground opening (navigating to Reader).

# Task Dependencies
- Task 2 depends on Task 1.
