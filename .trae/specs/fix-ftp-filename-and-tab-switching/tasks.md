# Tasks

- [x] Task 1: Fix FTP and SMB Filename Generation <!-- id: 0 -->
    - Modify `FilesFragment.downloadFtpToLocal` to use sanitized `displayName`.
    - Modify `FilesFragment.downloadSmbToLocal` to use sanitized `displayName`.
- [x] Task 2: Implement Tab Switching Logic <!-- id: 1 -->
    - Update `ReaderTabManager` interface and `DefaultReaderTabManager` implementation to track `currentTabId`.
    - Update `ReaderViewModel` to expose `currentTabId`.
- [x] Task 3: Update ReaderFragment Navigation <!-- id: 2 -->
    - Refactor `ReaderFragment` to observe `currentTabId` and switch content accordingly.
- [x] Task 4: Verify Fixes <!-- id: 3 -->
